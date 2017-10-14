package blog.codeninja.ku

import java.awt.Desktop
import java.net.URI
import java.util.regex.Pattern
import monix.eval._
import monix.execution._
import monix.reactive.subjects._
import scala.collection.JavaConverters._
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.beans.property.ObjectProperty
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.control.{Label, ListView, TextField}
import scalafx.scene.layout.{BorderPane, VBox}

class View(val agg: Aggregator) extends BorderPane {
  import Scheduler.Implicits.global

  // initial window sizing
  prefWidth = 740
  prefHeight = 800

  // search term to filter headlines through
  val search = PublishSubject[Pattern]()

  // filter the headlines with the latest search term
  val filteredHeadlines = agg.headlines.combineLatestMap(search) {
    (unread, s) => unread filter { h => s.matcher(h.title).find }
  }

  // currently selected headline
  val headline = ObjectProperty[Headline](this, "headline")

  // property evaluates to true when the headline is set
  val selected = headline =!= null

  // launch the browser and open the current headline
  def open = Option(headline.getValue) foreach (_.open)

  // create the content body list of all headlines
  center = new ListView[Headline] {
    styleClass = Seq("headlines")
    stylesheets = Seq("/headlines.css", "/scrollbar.css")

    // whenever the selection changes, publish it
    selectionModel().selectedItem.onChange {
      (_, _, h) => headline update h
    }

    // open double clicked headline
    onMousePressed = { e => if (e.getClickCount > 1) open }

    // whenever new headlines are available, update the list
    filteredHeadlines foreach { unread =>
      Platform runLater {
        items = ObservableBuffer(unread)
      }
    }
  }

  // search bar at the bottom
  bottom = new TextField {
    styleClass = Seq("search")
    stylesheets = Seq("/search.css")

    // pressing '/' will focus the search box
    promptText = "/ to search..."
    padding = Insets(4)

    // whenever the text changes, update the search regex
    text.onChange { (_, _, s) =>
      search onNext Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE)
    }
  }

  // label showing number of headlines, feeds, etc.
  val info = new Label("Fetching headlines...") {
    styleClass = Seq("info")
    stylesheets = Seq("/info.css")

    padding = Insets(4)

    // whenever the feeds are updated, show how many, etc.
    agg.feeds foreach { feeds =>
      Platform runLater {
        text = s"${feeds.length} feeds with ${feeds.flatten.length} headlines"
      }
    }
  }

  // create a preview that updates whenever the selected headline changes
  val preview = new Preview(headline)

  // simple hack to get the info box to grow
  top = new VBox(info) {
    info.prefWidth <== width
  }

  // hide the preview whenever a headline is not selected
  right <== when (selected) choose preview otherwise (null: Preview)

  // iniitalize the search with nothing
  search onNext Pattern.compile("")
}
