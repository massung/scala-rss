package blog.codeninja.rss

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

  /**
   * Each URL creates a periodic task that downloads the feed.
   */
  val readers = prefs.urls map (aggregate _)

  /**
   * Every time a feed is fetched it is sent to this subject for aggregation.
   * This observable simply allows for observers to be aware when a feed has
   * been fetched.
   */
  val aggregator = PublishSubject[(String, SyndFeed)]()

  /**
   * Each time a feed is fetched it needs to be accumulated into a single 
   * container. The feeds map is each feed (keyed by the URL used to fetch
   * it). It is shared so multiple observers can watch it. 
   */
  val feeds = aggregator.scan(Map[String, SyndFeed]())(_ + _).share

  /**
   * Each time the feeds map is updated with a new feed, all the headlines are
   * extracted, parsed, and sorted. The headlines are then filtered by age
   * and whether or not they are hidden from the user based on preferences.
   */
  val headlines = feeds.map(_.values.flatMap(extractHeadlines _).toList.sorted)
    .onErrorRestartUnlimited
    .map(_ filterNot (isOld _))
    .map(_ filterNot (isHidden _))
   
  /**
   * Keep a map of URI -> parsed headlines. If a headline has already been
   * parsed once, it shouldn't be parsed again. This is done because some feeds
   * don't post information like date/time, and each time the feed is updated
   * that information shouldn't be reset.
   */
  val cache = HashMap[String, Headline]()

  /**
   * Generate a list of regular expressions that - when matched - hide the
   * headlines extracted.
   */
  val hideFilters = prefs.filters map {
    s => Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE)
  }

  /**
   * Stop aggregating and cancel all periodic feed download tasks. 
   */
  def cancel = {
    aggregator.onComplete
    readers.foreach(_.cancel)
  }

  /**
   * True if the Headline is older than the desired age in the preferences. 
   */
  def isOld(h: Headline) = prefs.age.map(h.age.toDuration.isLongerThan _).getOrElse(false)

  /**
   * True if the Headline title matches any of the hideFilter patterns.
   */
  def isHidden(h: Headline) = hideFilters.exists(p => p.matcher(h.title).find)

  /**
   * Create a scheduled task that will periodically attempt to download the
   * RSS feed at a given URL.
   */
  def aggregate(url: String) = {
    Observable.intervalAtFixedRate(1.second, 5.minutes) foreach { _ =>
      Try(download(url)) match {
        case Success(feed) => aggregator onNext feed
        case Failure(ex)   => scribe error s"$url ${ex.toString}"
      }
    }
  }

  /**
   * Download the URL as an RSS feed and parse it. Handle following redirects
   * and throw an exception if the feed isn't there.
   */
  def download(url: String, redirects: Int = 5): (String, SyndFeed) = {
    Http(url).timeout(5000, 10000).asBytes match {
      case r if r.isRedirect && redirects > 0 =>
        download(r.location.get, redirects-1)
      case r if !r.isSuccess =>
        throw new Exception(r.statusLine)
      case r =>
        val input = new ByteArrayInputStream(r.body)
        val feed = new SyndFeedInput().build(new XmlReader(input))

        // output that this feed was parsed
        scribe info url
        url -> feed
    }
  }

  /**
   * Walk the entries of a feed and parse each into a Headline. If the entry
   * has already been parsed (in the cache), use that instead.
   */
  def extractHeadlines(feed: SyndFeed): List[Headline] = {
    feed.getEntries.asScala.toList flatMap { entry =>
      Try(cache.getOrElseUpdate(entry.getUri, new Headline(feed, entry))).toOption
    }
  }
}
