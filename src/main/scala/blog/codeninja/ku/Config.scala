package blog.codeninja.ku

import java.awt.Desktop
import java.io.{File, PrintWriter}
import java.nio.file._
import java.util.concurrent.TimeUnit
import monix.reactive.subjects._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write
import org.slf4j.{Logger, LoggerFactory}
import scala.io.Source
import scala.util.{Success, Try}

object Config {
  implicit val formats: Formats = DefaultFormats

  val logger = LoggerFactory getLogger "Config"

  // definition of the preferences file
  case class Prefs(
    val urls: List[String] = List.empty,
    val ageLimit: Option[String] = None,
    val filters: List[String] = List.empty,
  )

  // home folder where the dot file is saved
  val home: Path = Paths.get(System getenv "USERPROFILE")

  // dot file holding preferences in json format
  val file: File = home.resolve("ku.json").toFile

  // observable list of all urls that should be read
  val prefs = PublishSubject[Prefs]()

  // open the preferences file in the default editor
  def open: Unit =
    if (Try(Desktop.getDesktop open file).isFailure) {
      val writer = new PrintWriter(file)

      // create the default preferences fil
      writer.write(write(new Prefs))
      writer.close

      // try again
      open
    }

  // read the preferences file
  def load: Unit = {
    logger info "Reloading preferences..."

    Try(parse(Source.fromFile(file).mkString))
      .flatMap { json => Try(json.extract[Prefs]) }
      .orElse { Success(new Prefs) }
      .foreach (prefs onNext _)
  }

  // create a new watch service
  val service = FileSystems.getDefault.newWatchService

  // watches for changes in the user's home directory
  home.register(service, StandardWatchEventKinds.ENTRY_MODIFY)

  // set to true when the watch thread should terminate
  private var cancelRequested = false

  // create a thread that looks for changes to the config file
  val watch = new Thread {
    def handleEvent(e: WatchEvent[_]): Unit =
      e.context match {
        case p: Path if p.getFileName.toString == file.getName => load
        case _                                                 => ()
      }

    // loop forever, handling all events
    override def run = {
      while (!cancelRequested) {
        val key = Option(service.poll(500, TimeUnit.MILLISECONDS))

        // loop over all the events posted looking for modifications
        key foreach { key =>
          key.pollEvents.toArray foreach {
            case e: WatchEvent[_] => handleEvent(e)
            case _                => ()
          }

          // prepare the next event
          key.reset
        }
      }
    }
  }

  // elegantly ask the stop thread to terminate
  def cancel: Unit = cancelRequested = true

  // start the thread
  watch.start
}
