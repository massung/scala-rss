package blog.codeninja.ku

import com.rometools.rome.io._
import java.net.URL
import monix.execution._
import monix.reactive._
import monix.reactive.subjects._
import scala.collection.JavaConverters._
import scala.concurrent.duration._

class Aggregator(urls: String*) {
  import Scheduler.Implicits.global

  // create a cancelable for all the urls
  val readers = urls.map(url => aggregate(new URL(url)))

  // pull all the readers together into a single observable
  val feeds = Observable.combineLatestList(readers: _*)

  // all headlines are an aggregated, sorted list of all the feeds
  val headlines = feeds map (_.flatten.sorted)

  // create a scheduled task that reads the given RSS feed
  def aggregate(url: URL): Observable[List[Headline]] =
    Observable.intervalAtFixedRate(0.minutes, 1.minutes)
      .map(_ => readFeed(url))
      .onErrorHandleWith {
        case _ => aggregate(url)
      }

  // download the RSS feed, add it to the feed list, and update the view
  def readFeed(url: URL): List[Headline] = {
    val feed = new SyndFeedInput().build(new XmlReader(url))
    val entries = feed.getEntries.asScala map (new Headline(feed, _))

    // publish a new feed map with new headlines
    entries.toList
  }
}
