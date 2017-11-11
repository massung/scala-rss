package blog.codeninja.rss

import java.awt.Desktop
import java.net.URI
import monix.eval._
import monix.execution._
import netscape.javascript.JSObject
import org.w3c.dom.html.HTMLAnchorElement
import scala.io.Source
import scalafx.Includes._
import scalafx.beans.property.ObjectProperty
import scalafx.concurrent.Worker
import scalafx.scene.web.WebView
import scalatags.Text.all._

class Preview(val headline: ObjectProperty[Headline]) extends WebView {
  import Scheduler.Implicits.global

  // load the styles
  val styles = Source.fromFile(getClass.getResource("/preview.css").toURI).mkString

  // style the scrollbar and browser view
  stylesheets = Seq("/browser.css")

  // fix the size of the preview
  prefWidth = 360

  // keep track of the currently previewed headline
  var previewedHeadline: Headline = _

  // whenever the headline changes, update the HTML body
  headline onChange { (_, _, h) =>
    if (h != null && !h.equals(previewedHeadline)) {
      val template =
        html(
          head(scalatags.Text.tags2.style(styles)),
          body(
            div(cls := "title", a(href := h.entry.getLink, h.title)),
            div(cls := "feed", a(href := h.feed.getLink, h.feed.getTitle)),
            div(cls := "date", h.date.toString),
            div(cls := "content", raw(h.summary)),
            div(cls := "media",
              for (media <- h.audio) yield {
                div(audio(src := media.getUrl, `type` := media.getType, attr("controls") := "true"))
              },
              for (media <- h.video) yield {
                div(video(src := media.getUrl, `type` := media.getType, attr("controls") := "true"))
              },
            )
          )
        )

      // selection goes away when the list updates with new headlines
      previewedHeadline = h

      // write the preview html
      engine loadContent template.toString
    }
  }

  // whenever the template is finished loading, fix all links
  engine.delegate.getLoadWorker.stateProperty addListener {
    (_, _, state) => fixLinks(state)
  }

  // ku object for opening links in browser
  class UrlClicker() {
    def open(url: String) = Desktop.getDesktop browse new URI(url)
  }

  // change all the links to open the default browser
  def fixLinks(state: Worker.State): Unit = {
    if (state == Worker.State.Succeeded) {
      val doc = engine.getDocument
      val nodes = doc.getElementsByTagName("a")

      // loop over all the anchors and add onclick event handlers
      for (i <- 0 until nodes.getLength) {
        nodes.item(i) match {
          case a: HTMLAnchorElement => Option(a.getHref) foreach { href =>
            val attr = doc.createAttribute("onclick")

            // set the link to open when clicked
            attr.setValue(s"ku.open('$href'); return false;")

            // add the attribute or override the existing one
            a.getAttributes.setNamedItem(attr)
          }

          // shouldn't happen, but sometimes does
          case _ => ()
        }
      }

      // create the 'ku' object that opens links in browser
      engine.executeScript("window") match {
        case window: JSObject => window.setMember("ku", new UrlClicker)
      }
    }
  }
}
