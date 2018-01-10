package blog.codeninja.rss

import com.rometools.rome.feed.synd.{SyndContent, SyndEntry, SyndFeed}
import java.awt.Desktop
import java.net.URI
import java.util.Date
import org.joda.time.{DateTime, Duration, Interval}
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import scala.collection.JavaConverters._
import scalafx.beans.property.BooleanProperty

class Headline(val feed: SyndFeed, val entry: SyndEntry) extends Comparable[Headline] {
  /** Clean and parse the title as HTML.
    */
  val title: String = Jsoup.parse(Jsoup.clean(entry.getTitle.replace('\n', ' '), Whitelist.none)).text

  /** Get the optional description of the Headline. Then parse parse and clean
    * the HTML inside it as a summary.
    */
  val contents: Option[SyndContent] = entry.getContents.asScala.headOption
  val description: String = contents orElse Option(entry.getDescription) map (_.getValue) getOrElse ""
  val summary: String = Jsoup.clean(description, Whitelist.relaxed)
  val body: String = Jsoup.parse(summary).body.text

  /** Get the published date of this Headline. If there is no updated date set,
    * use the published date of the feed. If that isn't set, use the date now.
    */
  val date = Option(entry.getUpdatedDate)
    .orElse(Option(entry.getPublishedDate))
    .getOrElse(new Date)

  /** Get all the media enclosures from the Headline.
    */
  val media = entry.getEnclosures.asScala.toList

  /** Split the media enclosures into audio and video.
    */
  val audio = media.filter(_.getType startsWith "audio/")
  val video = media.filter(_.getType startsWith "video/")

  /** If the Headline has any media enclosures, show a media symbol in the
    * string.
    */
  val delim = if (audio.length > 0 || video.length > 0) '\u25b8' else ' '

  /** True when the headline has been marked as read.
    */
  val isRead = new BooleanProperty(this, "read", false)

  /** Calculate the age of the Headline.
    */
  def age = {
    val time = new DateTime(date)

    if (time.isBeforeNow) {
      new Interval(time, DateTime.now)
    } else {
      new Interval(0, 0)
    }
  }

  /** Convert the age of the Headline into a string.
    */
  def ageString = {
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

  /** True if a Headline belongs to a given feed. This exists since Headlines
    * are cached and the feed object can change.
    */
  def belongsTo(f: SyndFeed) =
    f == feed || ((f.getUri, feed.getUri) match {
      case (null, _) | (_, null) => f.getLink == feed.getLink
      case (a, b)                => a == b
    })


  /** Launch the default web browser to the Headline link.
    */
  def open = Desktop.getDesktop browse new URI(entry.getLink)

  /** True if this headline is the same as another.
    */
  def matchesHeadline(h: Headline) =
    (entry.getUri == h.entry.getUri) || (entry.getLink == h.entry.getLink)

  /** Show the age and title of the Headline.
    */
  override def toString = s"$ageString $delim $title"

  /** Headlines are the same if they resolve to the same end-point.
    */
  override def equals(obj: Any): Boolean =
    obj match {
      case h: Headline => matchesHeadline(h)
      case s: String   => entry.getLink == s
      case _           => false
    }

  /** Headlines are sorted by date and then by title.
    */
  override def compareTo(h: Headline): Int =
    h.date compareTo date match {
      case 0 => entry.getTitle compareTo h.entry.getTitle
      case c => c
    }

  /** Hash by link.
    */
  override def hashCode = entry.getLink.hashCode
}
