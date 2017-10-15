package blog.codeninja.ku

import monix.eval._
import monix.execution._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.slf4j.{Logger, LoggerFactory}
import scala.util._
import scalafx.Includes._
import scalaj.http._

object WebParser {
  import Scheduler.Implicits.global

  implicit val formats: Formats = DefaultFormats

  case class ParseResponse(
    val title: String,
    val content: String,
    val date_published: String,
    val lead_image_url: String,
    val url: String,
    val domain: String,
  )

  val logger = LoggerFactory getLogger "Parser"

  var cancelable: Option[Cancelable] = None

  // whenever the headline changes, update the HTML body
  def parseUrl(url: String)(andThen: ParseResponse => Unit): Unit = {
    cancelable foreach (_.cancel)

    // build the parse request
    val req = Http("https://mercury.postlight.com/parser")
      .param("url", url)
      .header("Content-Type", "application/json")
      .header("x-api-key", "<api key here>")

    // background task to request and parse
    val task = Task {
      parse(req.asString.body).extract[ParseResponse]
    }

    // execute the task
    cancelable = Some(task runOnComplete {
      case Failure(e) => logger error e.toString
      case Success(r) => andThen(r)
    })
  }
}
