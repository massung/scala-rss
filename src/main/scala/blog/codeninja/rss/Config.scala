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
import scala.util._
import scribe._

object Config {
  implicit val formats: Formats = DefaultFormats

  /** Whenever the preferences are loaded, send them to this observable.
    */
  val prefs = PublishSubject[Prefs]()

  /** Create a time period parser for limiting the age of a headline.
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

  /** Where are the config files located.
    */
  val home = List("HOME", "USERPROFILE")
    .flatMap(env => Option(System getenv env))
    .headOption
    .map(Paths.get(_))
    .getOrElse(Paths.get("/"))

  /** Find the user's HOME path and the preferences file within it.
    */
  val file = home.resolve("rss.json")

  /** Create a file watcher on the preferences file.
    */
  val watcher = new Watcher(file)(load _)

  /** Load the file and publish the new preferences.
    */
  def load: Unit = {
    scribe info "Reloading preferences..."

    // load the source file and parse it as JSON
    if (Files.exists(file) && Files.isRegularFile(file)) {
      val source = Source.fromFile(file.toFile).mkString

      // update the preferences
      Try(parse(source)) foreach {
        json => Option(json.extract[Prefs]) foreach (prefs.onNext _)
      }
    }
  }

  /** Launch the default editor and let the user modify the preferences.
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
}
