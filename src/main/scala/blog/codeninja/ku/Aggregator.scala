package blog.codeninja.ku

import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io._
import org.joda.time.format.PeriodFormatterBuilder
import org.joda.time.Period
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.regex.Pattern
import monix.execution._
import monix.execution.atomic._
import monix.reactive._
import monix.reactive.subjects._
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util._
import scala.util.Try
import scalaj.http._
import org.slf4j.LoggerFactory

class Aggregator(prefs: Config.Prefs) {
  import Scheduler.Implicits.global

  // slf4j logger
  val logger = LoggerFactory getLogger "Aggregator"

  val age = {
    val parser = new PeriodFormatterBuilder()
      .appendWeeks().appendSuffix("w")
      .appendDays().appendSuffix("d")
      .appendHours().appendSuffix("h")
      .appendMinutes().appendSuffix("m")
      .appendSeconds().appendSuffix("s")
      .toFormatter()

    prefs.ageLimit flatMap {
      limit => Try(Period.parse(limit, parser)).toOption map (_.toStandardDuration)
    }
  }

  // filter patterns of headlines to hide
  val hideFilters = prefs.filters map {
    s => Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE)
  }

  // create a cancelable, periodic reader for each url
  val readers = prefs.urls map (aggregate _)

  // as each feed is read, it is sent to this subject
  val subject = PublishSubject[(String, (SyndFeed, List[Headline]))]()

  // fold all the feeds read together
  val aggregatedFeeds = subject.scan(Map[String, (SyndFeed, List[Headline])]())(_ + _)

  // all the feeds, but multicast as a hot observable
  val hotFeeds = aggregatedFeeds.publishSelector(x => x)

  // all the feeds, as a unique list
  val feeds = hotFeeds.map(_.values.map(_._1).toList.sortBy(_.getTitle))

  // transform the feeds downloaded into a list of sorted headlines
  val headlines = hotFeeds.map(_.values.map(_._2).flatten.toList.sorted)
    .map(_ filterNot (isOld _))
    .map(_ filterNot (isHidden _))

  // stop running the aggregator
  def cancel = {
    subject.onComplete
    readers.foreach(_.cancel)
  }

  // true if the age of the headline exceeds the age limit in the preferences
  def isOld(h: Headline) = age.map(h.age.toDuration.isLongerThan _).getOrElse(false)

  // true if this headlnie should be hidden from the user
  def isHidden(h: Headline) = hideFilters.exists(p => p.matcher(h.title).find)

  // create a scheduled task that reads the given RSS feed
  def aggregate(url: String) =
    Observable.intervalAtFixedRate(1.second, 5.minutes) foreach { _ =>
      Try(readFeed(url)) match {
        case Success(feed) => subject onNext (url -> feed)
        case Failure(ex)   => logger error s"$url ${ex.toString}"
      }
    }

  // download the RSS feed, add it to the feed list, and update the view
  def readFeed(url: String, redirects: Int = 5): (SyndFeed, List[Headline]) = {
    Http(url).timeout(5000, 10000).asBytes match {
      case r if r.isRedirect && redirects > 0 =>
        readFeed(r.location.get, redirects-1)
      case r if !r.isSuccess =>
        throw new Exception(r.statusLine)
      case r =>
        val input = new ByteArrayInputStream(r.body)
        val feed = new SyndFeedInput().build(new XmlReader(input))
        val entries = feed.getEntries.asScala map (new Headline(feed, _))

        // output that this feed was parsed
        logger info url

        // publish a new feed map with new headlines
        feed -> entries.toList
    }
  }
}
