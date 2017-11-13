package blog.codeninja.rss

import monix.eval._
import monix.execution._
import org.json4s._
import org.json4s.native.JsonMethods._
import scala.util._
import scalafx.Includes._
import scalaj.http._
import scribe._

object WebParser {
  import Scheduler.Implicits.global

  implicit val formats: Formats = DefaultFormats

  /**
   * JSON response for a Mercury request.
   */
  case class ParseResponse(
    val title: String,
    val content: String,
    val datePublished: String,
    val leadImageUrl: String,
    val url: String,
    val domain: String,
  )

  /**
   * The current request being issued. If - while waiting - the user
   * initiates a new request, cancel the existing one.
   */
  var cancelable: Option[Cancelable] = None

  /**
   * Issue a request to parse a URL and get the content from it. Cancel
   * any existing request before issuing the new one. When done, pass the
   * response to a function.
   */
  def parseUrl(url: String)(andThen: ParseResponse => Unit): Unit = {
    cancelable foreach (_.cancel)

    // build the parse request
    val req = Http("https://mercury.postlight.com/parser")
      .param("url", url)
      .header("Content-Type", "application/json")
      .header("x-api-key", "<api key here>")

    // background task to request and parse
    val task = Task {
      parse(req.asString.body).camelizeKeys.extract[ParseResponse]
    }

    // execute the task
    cancelable = Some(task runOnComplete {
      case Failure(e) => scribe error e.toString
      case Success(r) => andThen(r)
    })
  }
}
