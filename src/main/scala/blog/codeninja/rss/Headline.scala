package blog.codeninja.rss

import com.rometools.rome.feed.synd.{SyndEntry, SyndFeed}
import java.awt.Desktop
import java.net.URI
import java.util.Date
import org.joda.time.{DateTime, Duration, Interval}
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import scala.collection.JavaConverters._

class Headline(val feed: SyndFeed, val entry: SyndEntry) extends Comparable[Headline] {
  val singleLine: String = entry.getTitle.replace('\n', ' ')
  val title: String = Jsoup.parse(Jsoup.clean(singleLine, Whitelist.none)).text

  // extract the summary as HTML and then remove tags from it
  val description: String = Option(entry.getDescription) map (_.getValue) getOrElse ""
  val summary: String = Jsoup.clean(description, Whitelist.relaxed)

  // when was this headline last updated or published
  val date: Date = Option(entry.getUpdatedDate) orElse Option(entry.getPublishedDate) getOrElse new Date()

  // media enclosures (video and audio)
  val media = entry.getEnclosures.asScala.toList

  // split the media enclosures into audio and video
  val audio = media.filter(_.getType startsWith "audio/")
  val video = media.filter(_.getType startsWith "video/")

  // unicode graphemes between the age and title
  val delim = if (audio.length > 0 || video.length > 0) '\u25b8' else ' '

  // calculate the age of the
  def age: Interval = {
    val time = new DateTime(date)

    if (time.isBeforeNow) {
      new Interval(time, DateTime.now)
    } else {
      new Interval(0, 0)
    }
  }

  // calculate the age of the headline by time
  def ageString: String = {
    val period = age.toPeriod

    // convert to short text
    if (period.getYears > 0) "> 1y"
    else if (period.getMonths > 0) "> 4w"
    else if (period.getWeeks > 0) f"${period.getWeeks}%3dw"
    else if (period.getDays > 0) f"${period.getDays}%3dd"
    else if (period.getHours > 0) f"${period.getHours}%3dh"
    else if (period.getMinutes > 0) f"${period.getMinutes}%3dm"
    else "< 1m"
  }

  // true if this headline belongs to a given feed
  def belongsTo(f: SyndFeed) =
    f == feed || ((f.getUri, feed.getUri) match {
      case (null, _) | (_, null) => f.getLink == feed.getLink
      case (a, b)                => a == b
    })

  // launch the default browser to the headline
  def open = Desktop.getDesktop browse new URI(entry.getLink)

  // age and title of the headline
  override def toString = s"$ageString $delim $title"

  // headlines are the same if they point to the same link
  override def equals(obj: Any): Boolean =
    obj match {
      case h: Headline => entry.getLink == h.entry.getLink
      case s: String   => entry.getLink == s
      case _           => false
    }

  // sorting by date, then by title
  override def compareTo(h: Headline): Int =
    h.date compareTo date match {
      case 0 => entry.getTitle compareTo h.entry.getTitle
      case c => c
    }

  // hash by link
  override def hashCode = entry.getLink.hashCode
}
