package blog.codeninja.ku

import com.rometools.rome.io._
import java.io.ByteArrayInputStream
import monix.execution._
import monix.reactive._
import monix.reactive.subjects._
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scalaj.http._
import org.slf4j.{Logger, LoggerFactory}

class Aggregator(urls: String*) {
  import Scheduler.Implicits.global

  val logger = LoggerFactory getLogger "Ku"

  // create a cancelable for all the urls
  val readers = urls map (aggregate _)

  // pull all the readers together into a single observable
  val feeds = Observable.combineLatestList(readers: _*)

  // all headlines are an aggregated, sorted list of all the feeds
  val headlines = feeds map (_.flatten.sorted)

  // create a scheduled task that reads the given RSS feed
  def aggregate(url: String): Observable[List[Headline]] =
    Observable.intervalAtFixedRate(1.second, 1.minutes)
      .flatMap(_ => readFeed(url))
      .onErrorHandle {
        case e => logger error e.toString; List.empty
      }

  // download the RSS feed, add it to the feed list, and update the view
  def readFeed(url: String): Observable[List[Headline]] = {
    Http(url).timeout(5000, 10000).asBytes match {
      case r if r.isRedirect => readFeed(r.location.get)
      case r if r.isSuccess => {
        val is = new ByteArrayInputStream(r.body)
        val feed = new SyndFeedInput().build(new XmlReader(is))
        val entries = feed.getEntries.asScala map (new Headline(feed, _))

        // output that this feed was parsed
        logger info url

        // publish a new feed map with new headlines
        Observable.now(entries.toList)
      }

      // anything else is an error
      case r => throw new Error(r.statusLine)
    }
  }
}
