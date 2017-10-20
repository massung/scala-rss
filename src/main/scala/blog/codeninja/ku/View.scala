package blog.codeninja.ku

import java.awt.Desktop
import java.net.URI
import java.util.regex.Pattern
import monix.eval._
import monix.execution._
import monix.reactive._
import monix.reactive.subjects._
import scala.collection.JavaConverters._
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.beans.property.ObjectProperty
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.control.{Label, ListCell, ListView, TextField}
import scalafx.scene.input.KeyCode
import scalafx.scene.layout.{BorderPane, VBox}

class View(val agg: Observable[Aggregator]) extends BorderPane {
  import Scheduler.Implicits.global

  // initial window sizing
  prefWidth = 740
  prefHeight = 800

  // search term to filter headlines through
  val search = BehaviorSubject[Pattern](Pattern.compile(""))

  // archived headlines list
  var archive = BehaviorSubject[List[Headline]](List.empty)

  // filter the headlines with the latest search term
  val filteredHeadlines = agg flatMap { agg =>
    agg.headlines.combineLatestMap(search) {
      (all, s) => all filter (h => s.matcher(h.title).find)
    }
  }

  // unread headlines aren't in the archive list
  val unreadHeadlines = filteredHeadlines.combineLatestMap(archive) {
    (all, read) => all filterNot (read contains _)
  }

  // currently selected headline
  val headline = ObjectProperty[Headline](this, "headline")

  // launch the browser and open the current headline
  def open(): Unit = Option(headline.getValue) foreach (_.open)

  // create the content body list of all headlines
  val list = new ListView[Headline] {
    styleClass = Seq("headlines")
    stylesheets = Seq("/headlines.css", "/scrollbar.css")

    // whenever the selection changes, publish it
    selectionModel().selectedItem.onChange {
      (_, _, h) => headline update h
    }

    // custom cells to truncate overflow
    cellFactory = { _ =>
      val w = prefWidth

      new ListCell[Headline] {
        prefWidth <== w - 20
        ellipsisString = "\u2026"

        item.onChange { (_, _, h) =>
          text = Option(h) map(_.toString) getOrElse null
        }
      }
    }

    // open double clicked headline
    onMousePressed = { e => if (e.getClickCount > 1) open }

    // clear selection and open browser
    onKeyPressed = { e =>
      e.code match {
        case KeyCode.X      => ()
        case KeyCode.U      => ()
        case KeyCode.Escape => selectionModel() select null
        case KeyCode.Enter  => open
        case _              => ()
      }
    }
  }

  // whenever new aggregator updates headlines, update the list
  var update = unreadHeadlines foreach { unread =>
    Platform runLater {
      list.items = ObservableBuffer(unread)
    }
  }

  // label showing number of headlines, feeds, etc.
  val info = new Label("[x] - archive headline; [u] - unarchive") {
    styleClass = Seq("info")
    stylesheets = Seq("/info.css")

    padding = Insets(4)
  }

  // create a preview that updates whenever the selected headline changes
  val preview = new Preview(headline)

  // simple hack to get the info box to grow
  top = new VBox(new MainMenu(headline), info) {
    info.prefWidth <== width
  }

  // main body contains headlines
  center = list

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

  // hide the preview whenever a headline is not selected
  right <== when (headline =!= null) choose preview otherwise (null: Preview)
}
