package blog.codeninja.ku

import com.rometools.rome.feed.synd.{SyndEntry, SyndFeed}
import java.awt.Desktop
import java.net.URI
import java.util.Date
import org.joda.time.{DateTime, Interval}
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist

class Headline(val feed: SyndFeed, val entry: SyndEntry) extends Comparable[Headline] {
  val singleLine: String = entry.getTitle.replace('\n', ' ')
  val title: String = Jsoup.parse(Jsoup.clean(singleLine, Whitelist.none)).text

  // extract the summary as html and then remove tags from it
  val description: String = Option(entry.getDescription) map (_.getValue) getOrElse ""
  val summary: String = Jsoup.clean(description, Whitelist.relaxed)

  // when was this headline last updated or published
  val date: Date = Option(entry.getUpdatedDate) getOrElse entry.getPublishedDate

  // calculate the age of the headline by time
  def age: String = {
    val time = new DateTime(date)
    val interval = if (time.isBeforeNow) new Interval(time, DateTime.now) else new Interval(0, 0)
    val period = interval.toPeriod

    // convert to short text
    if (period.getYears > 0) "> 1y"
    else if (period.getMonths > 0) "> 4w"
    else if (period.getWeeks > 0) f"${period.getWeeks}%3dw"
    else if (period.getDays > 0) f"${period.getDays}%3dd"
    else if (period.getHours > 0) f"${period.getHours}%3dh"
    else if (period.getMinutes > 0) f"${period.getMinutes}%3dm"
    else "< 1m"
  }

  // launch the default browser to the headline
  def open = Desktop.getDesktop browse new URI(entry.getLink)

  // age and title of the headline
  override def toString = s"$age - $title"

  // hash by link
  override def hashCode = entry.getLink.hashCode

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
}
