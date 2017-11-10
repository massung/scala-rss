package blog.codeninja.ku

import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io._
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.regex.Pattern
import monix.execution._
import monix.execution.atomic._
import monix.reactive._
import monix.reactive.subjects._
import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap
import scala.concurrent.duration._
import scala.util._
import scala.util.Try
import scalaj.http._
import scribe._

class Aggregator(prefs: Config.Prefs) {
  import Scheduler.Implicits.global

  // filter patterns of headlines to hide
  val hideFilters = prefs.filters map {
    s => Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE)
  }

  // create a periodic reader for each URL
  val readers = prefs.urls map (aggregate _)

  // as each feed is read, it is sent to this consumer
  val consumer = PublishSubject[(String, SyndFeed)]()

  // fold the consumed feeds together into a single map
  val feeds = consumer.scan(Map[String, SyndFeed]())(_ + _).share

  // transform the feeds into a list of sorted headlines
  val headlines = feeds.map(_.values.flatMap(parseEntries _).toList.sorted)
    .onErrorRestartUnlimited
    .map(_ filterNot (isOld _))
    .map(_ filterNot (isHidden _))
   
  // cached headlines
  val cache = HashMap[String, Headline]()

  // stop running the aggregator
  def cancel = {
    consumer.onComplete
    readers.foreach(_.cancel)
  }

  // true if the age of the headline exceeds the age limit in the preferences
  def isOld(h: Headline) = prefs.age.map(h.age.toDuration.isLongerThan _).getOrElse(false)

  // true if this headline should be hidden from the user
  def isHidden(h: Headline) = hideFilters.exists(p => p.matcher(h.title).find)

  // create a scheduled task that reads the given RSS feed
  def aggregate(url: String) =
    Observable.intervalAtFixedRate(1.second, 5.minutes) foreach { _ =>
      Try(readFeed(url)) match {
        case Success(feed) => consumer onNext (url -> feed)
        case Failure(ex)   => scribe error s"$url ${ex.toString}"
      }
    }

  // download the RSS feed, add it to the feed list, and update the view
  def readFeed(url: String, redirects: Int = 5): SyndFeed = {
    Http(url).timeout(5000, 10000).asBytes match {
      case r if r.isRedirect && redirects > 0 =>
        readFeed(r.location.get, redirects-1)
      case r if !r.isSuccess =>
        throw new Exception(r.statusLine)
      case r =>
        val input = new ByteArrayInputStream(r.body)
        val feed = new SyndFeedInput().build(new XmlReader(input))

        // output that this feed was parsed
        scribe info url
        feed
    }
  }

  // create a list of headlines from a feed
  def parseEntries(feed: SyndFeed): List[Headline] = {
    feed.getEntries.asScala.toList flatMap { entry =>
      Try(cache.getOrElseUpdate(entry.getUri, new Headline(feed, entry))).toOption
    }
  }
}
