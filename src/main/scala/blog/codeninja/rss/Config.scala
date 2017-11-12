package blog.codeninja.rss

import java.awt.Desktop
import java.io.{File, PrintWriter}
import java.nio.file._
import monix.reactive.subjects._
import org.joda.time.format.PeriodFormatterBuilder
import org.joda.time.Period
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write
import scala.io.Source
import scala.util.{Success, Try}
import scribe._

object Config {
  implicit val formats: Formats = DefaultFormats

  /**
   * Whenever the preferences are loaded, send them to this observable.
   */
  val prefs = PublishSubject[Prefs]()
  
  /**
   * Create a time period parser for limiting the age of a headline.
   */
  val ageParser = new PeriodFormatterBuilder()
    .appendWeeks().appendSuffix("w")
    .appendDays().appendSuffix("d")
    .appendHours().appendSuffix("h")
    .appendMinutes().appendSuffix("m")
    .appendSeconds().appendSuffix("s")
    .toFormatter()

  // definition of the preferences file
  case class Prefs(
    val urls: List[String] = List.empty,
    val ageLimit: Option[String] = None,
    val filters: List[String] = List.empty,
  ) {
    val age = ageLimit flatMap { 
      s => Option(Period.parse(s, ageParser)) map (_.toStandardDuration)
    }
  }

  /**
   * Find the user's HOME path and the preferences file within it.
   */
  val file = Paths get (System getenv "USERPROFILE") resolve "rss.json"
  
  /**
   * Create a file watcher on the preferences file.
   */
  val watcher = new Watcher(file)(load _)

  /**
   * Load the file and publish the new preferences.
   */
  def load: Unit = {
    scribe info "Reloading preferences..."
        
    // extract the preferences or use a new set of preferences 
    val json = Source.fromFile(file.toFile).mkString
    val it = Try(parse(json))
      .flatMap (obj => Try(obj.extract[Prefs]))
      .getOrElse (new Prefs)
    
    // update the preferences 
    prefs onNext it
  }

  /**
   * Launch the default editor and let the user modify the preferences.
   */
  def open: Unit = {
    if (Try(Desktop.getDesktop open file.toFile).isFailure) {
      val writer = new PrintWriter(file.toFile)

      // create the default preferences file
      writer.write(write(new Prefs))
      writer.close

      // try again
      open
    }
  }
  
  // start watching for the preferences file to change
  watcher.start
  
  // load the preferences
  load
}
