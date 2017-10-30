package blog.codeninja.ku

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

object Aggregator {
  sealed trait Action

  // enumerated actions for the aggregator
  final case class Aggregate(url: String, headlines: List[Headline]) extends Action

  // atomic state object for subscribers, headlines, etc.
  final case class State(
    val archive: List[String] = List.empty,
    val feeds: Map[String, List[Headline]] = Map.empty,
  )
}

class Aggregator(prefs: Config.Prefs) {
  import Scheduler.Implicits.global
  import Aggregator._

  // create an initial state for the aggregator
  private val state = Atomic(new Aggregator.State)

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

  val feeds = new ActorSubject[Aggregator.Action, Map[String, List[Headline]]](Map.empty) {
    override def receive(old: Map[String, List[Headline]], msg: Aggregator.Action) = {
      msg match {
        case Aggregate(url, hs) => old + (url -> hs)
      }
    }
  }

  // transform the feeds downloaded into a list of sorted headlines
  val headlines = feeds map (_.values.flatten.toList.sorted.filterNot(isOld _).partition(isHidden _))

  // create a cancelable, periodic reader for all the urls
  val readers = prefs.urls map (aggregate _)

  // stop running the aggregator
  def cancel = readers foreach (_.cancel)

  // true if the age of the headline exceeds the age limit in the preferences
  def isOld(h: Headline) = age map (h.age.toDuration isLongerThan _) getOrElse false

  // true if this headlnie should be hidden from the user
  def isHidden(h: Headline) = hideFilters.exists(p => p.matcher(h.title).find)

  // create a scheduled task that reads the given RSS feed
  def aggregate(url: String) =
    Observable.intervalAtFixedRate(1.second, 5.minutes) foreach { _ =>
      Try(readFeed(url)) match {
        case Success(hs) => feeds onNext new Aggregator.Aggregate(url, hs)
        case Failure(ex) => logger error ex.toString
      }
    }

  // download the RSS feed, add it to the feed list, and update the view
  def readFeed(url: String, redirects: Int = 5): List[Headline] = {
    Http(url).timeout(5000, 10000).asBytes match {
      case r if r.isRedirect && redirects > 0 =>
        readFeed(r.location.get, redirects-1)
      case r if r.isSuccess => {
        val input = new ByteArrayInputStream(r.body)
        val feed = new SyndFeedInput().build(new XmlReader(input))
        val entries = feed.getEntries.asScala map (new Headline(feed, _))

        // output that this feed was parsed
        logger info url

        // publish a new feed map with new headlines
        entries.toList
      }

      // anything else is an error
      case r => throw new Error(r.statusLine)
    }
  }
}
