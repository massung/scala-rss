package blog.codeninja.ku

import java.awt.Desktop
import java.io.{File, PrintWriter}
import java.nio.file._
import monix.reactive.subjects._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write
import scala.io.Source
import scala.util.Try

object DotFile {
  implicit val formats: Formats = DefaultFormats

  //
  case class Prefs(val urls: List[String] = List.empty)

  // home folder where the dot file is saved
  val home: Path = Paths.get(System getenv "USERPROFILE")

  // dot file holding preferences in json format
  val file: File = home.resolve("ku.json").toFile

  // observable list of all urls that should be read
  val urls = BehaviorSubject[List[String]](List.empty)

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
  def load: Prefs =
    Try(parse(Source.fromFile(file).mkString))
      .flatMap { json => Try(json.extract[Prefs]) }
      .getOrElse { new Prefs }

  // create a thread that watches for the preferences file to change
  def watch: Thread = {
    val service = FileSystems.getDefault.newWatchService

    home.register(service, StandardWatchEventKinds.ENTRY_MODIFY)

    //
    new Thread {
      override def run {
        while (true) {
          val key = service.take

          // loop over all the events posted
          key.pollEvents.toArray foreach {
            case e: WatchEvent[_] =>
              e.context match {
                case c: Path if c.getFileName.toString == file.getName =>
                  //
                  println("here")

                // ignore all other files
                case _ => ()
              }

            // ignore all other events
            case _ => ()
          }

          // wait for the next event
          key.reset
        }
      }
    }
  }
}
