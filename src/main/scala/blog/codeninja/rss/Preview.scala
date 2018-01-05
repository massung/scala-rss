package blog.codeninja.rss

import java.awt.Desktop
import java.net.URI
import monix.eval._
import monix.execution._
import netscape.javascript.JSObject
import org.w3c.dom.html.{HTMLAnchorElement, HTMLImageElement}
import scala.io.Source
import scala.util.Try
import scalafx.Includes._
import scalafx.beans.property.ObjectProperty
import scalafx.concurrent.Worker
import scalafx.scene.web.WebView
import scalatags.Text.all._

class Preview(val headline: ObjectProperty[Headline]) extends WebView {
  import Scheduler.Implicits.global

  /** The CSS styles to be embedded in every preview.
    */
  val styles = Source.fromInputStream(getClass.getResourceAsStream("/preview.css")).mkString

  /** The styles to use for the actual FX control.
    */
  stylesheets = Seq("/browser.css")

  /** Fixed width size of the control.
    */
  prefWidth = 360

  /** Maintain a pointer to the currently previewed headline.
    */
  var previewedHeadline: Headline = _

  /** Whenever the currently selected Headline changes, update the preview.
    *
    * NOTE: The headline is separate from the previewedHeadline because when
    *       ListView is updated the selected headline observable will change.
    *       While the previewedHeadline will remain unchanged, but comparing
    *       them will return true.
    */
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

  /** When the HTML generated for the preview is done being loaded into the
    * browser engine, go through and fix-up all the anchor links.
    */
  engine.delegate.getLoadWorker.stateProperty addListener {
    (_, _, state) => fixLinks(new Worker.State(state))
  }

  /** Custom object users for handling anchor clicks so they are opened in
    * an external browser instead of relocating the preview.
    */
  class UrlClicker() {
    def open(url: String) = Desktop.getDesktop browse new URI(url)
  }

  /** Loop over all the links in the preview and change them so that - when
    * clicked - they open an external browser instead of changing the preview
    * HREF location.
    */
  def fixLinks(state: Worker.State): Unit = {
    if (state == Worker.State.Succeeded) {
      val doc = engine.getDocument
      val images = doc.getElementsByTagName("img")
      val anchors = doc.getElementsByTagName("a")

      // loop over images and remove floating attributes
      for (i <- 0 until images.getLength) {
        Option(images.item(i)) collect {
          case i: HTMLImageElement =>
            Option(i.getAttributes.getNamedItem("align")) foreach (_.setNodeValue("middle"))
        }
      }

      // loop over all the anchors and add click event handlers
      for (i <- 0 until anchors.getLength) {
        Option(anchors.item(i)) collect {
          case a: HTMLAnchorElement =>
            Option(a.getHref) foreach { href =>
              val attr = doc.createAttribute("onclick")

              // set the link to open when clicked
              attr.setValue(s"rss.open('$href'); return false;")

              // add the attribute or override the existing one
              a.getAttributes.setNamedItem(attr)
            }
        }
      }

      // create the object that opens links in browser
      engine.executeScript("window") match {
        case window: JSObject => window.setMember("rss", new UrlClicker)
      }
    }
  }
}
