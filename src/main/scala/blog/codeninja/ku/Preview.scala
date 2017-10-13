package blog.codeninja.ku

import scala.io.Source
import scalafx.beans.property.ObjectProperty
import scalafx.scene.web.WebView
import scalatags.Text.all._

class Preview(val headline: ObjectProperty[Headline]) extends WebView {
  val styles = Source.fromFile(getClass.getResource("/preview.css").toURI).mkString

  // fix the size of the preview
  prefWidth = 325

  // whenever the headline changes, update the HTML body
  headline onChange { (_, _, h) =>
    if (h != null) {
      val template =
        html(
          head(scalatags.Text.tags2.style(styles)),
          body(
            div(a(cls := "title", href := h.entry.getLink, h.title)),
            div(a(cls := "feed", href := h.feed.getLink, h.feed.getTitle)),
            div(cls := "date", h.date.toString),
            div(cls := "content", raw(h.summary)),
          )
        )

      // write the preview html
      engine loadContent template.toString
    }
  }
}
