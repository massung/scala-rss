package blog.codeninja.ku

import com.rometools.rome.feed.synd.SyndFeed
import java.awt.{Desktop, Toolkit}
import java.awt.datatransfer.StringSelection
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
import scalafx.scene.control.{Label, ListCell, ListView, MenuItem, SeparatorMenuItem, TextField}
import scalafx.scene.input.{KeyCode, KeyEvent}
import scalafx.scene.layout.{BorderPane, VBox}

class View(val agg: Observable[Aggregator]) extends BorderPane {
  import Scheduler.Implicits.global

  // initial window sizing
  prefWidth = 860
  prefHeight = 800

  // search term to filter headlines through
  val search = BehaviorSubject[Pattern](Pattern.compile(""))

  // filter visible headlines by the feed selected
  val feedFilter = BehaviorSubject[Option[SyndFeed]](None)

  // menu item to remove the feed filter
  val allItem = new MenuItem("All") {
    onAction = { _ => feedFilter onNext None }
  }

  // filter the headlines with the latest search term
  val filteredHeadlines = agg flatMap { agg =>
    agg.feeds foreach { feeds =>
      val items = feeds.values.toSeq.sortBy(_.getTitle) map { f =>
        new MenuItem(f.getTitle) {
          onAction = { _ => feedFilter onNext Some(f) }
        }
      }

      // update the view menu
      Platform runLater {
        menu.viewMenu.items = Seq(allItem, new SeparatorMenuItem) ++ items
      }
    }

    agg.headlines
      .combineLatestMap(feedFilter) { (headlines, feed) =>
        (feed, headlines filter (h => feed.map(h belongsTo _) getOrElse true))
      }
      .combineLatestMap(search) { case ((feed, headlines), search) =>
        val feedName = feed map (f => s"${f.getTitle} - ") getOrElse ""
        val matched = headlines.filter(h => search.matcher(h.title).find)

        Platform runLater {
          info.text = s"$feedName${matched.length} headlines; $infoText"
        }

        // return only the matched headlines
        matched
      }
  }

  // unread headlines aren't in the archive list
  val unreadHeadlines = filteredHeadlines.combineLatestMap(Archive) {
    (all, read) => all filterNot (read contains _)
  }

  // currently selected headline
  val headline = ObjectProperty[Headline](this, "headline")

  // launch the browser and open the current headline
  def open(): Unit =
    Option(headline.getValue) match {
      case Some(h) => h.open
      case None    => list.selectionModel().selectFirst
    }

  // put the link to the currently selected headline onto the clipboard
  def copy(full: Boolean): Unit =
    Option(headline.getValue) foreach { h =>
      val text = if (!full) h.entry.getLink else ""
      val sel = new StringSelection(text)

      // put the link into the clipboard
      Toolkit.getDefaultToolkit.getSystemClipboard.setContents(sel, null)
    }

  // add the selected headline to the archive
  def archive(next: Boolean = true): Unit = Option(headline.getValue) foreach { h =>
    val model = list.selectionModel()

    // move the selection
    if (next) {
      model.selectNext; if (model.isEmpty) {
        model.selectPrevious
      }
    } else {
      model.selectPrevious; if (model.isEmpty) {
        model.selectNext
      }
    }

    // archive the original selection
    Archive onNext Push(h)
  }

  // undo the previous archive action and select it
  def undoArchive(): Unit = Archive onNext Undo()

  // set the focus to the search field
  def doSearch(): Unit = Platform runLater { searchField.requestFocus }

  // clear the search and then preview
  def clear: Unit = {
    if (searchField.text.value != "") {
      searchField.text = ""
    } else {
      list.selectionModel() select null
    }
  }

  // shared event handler for controls
  def onKey(e: KeyEvent): Unit = {
    e.code match {
      case KeyCode.P                  => Config.open
      case KeyCode.X | KeyCode.Delete => archive(!e.shiftDown)
      case KeyCode.C                  => copy(e.controlDown)
      case KeyCode.U                  => undoArchive
      case KeyCode.Slash              => doSearch
      case KeyCode.Escape             => clear
      case KeyCode.Enter              => open
      case _                          => ()
    }
  }

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
    onMousePressed = {
      e => if (e.getClickCount > 1) open
    }

    // clear selection and open browser
    onKeyPressed = onKey
  }

  // whenever new aggregator updates headlines, update the list
  var update = unreadHeadlines foreach { unread =>
    Platform runLater {
      list.items = ObservableBuffer(unread)
    }
  }

  // common info text
  val infoText = "[ret] open; [esc] close; [x] archive; [u] undo; [/] search; [c] copy"

  // label showing number of headlines, feeds, etc.
  val info = new Label(infoText) {
    styleClass = Seq("info")
    stylesheets = Seq("/info.css")

    padding = Insets(4)
  }

  // create a preview that updates whenever the selected headline changes
  val preview = new Preview(headline) {
    //onKeyPressed = onKey
  }

  // field for filtering by title
  val searchField = new TextField {
    styleClass = Seq("search")
    stylesheets = Seq("/search.css")

    // pressing '/' will focus the search box
    promptText = "/ to search..."
    padding = Insets(4)

    // focus the list on enter
    onAction = { _ =>
      list.requestFocus

      // select the first item if nothing is selected
      if (headline.value == null) {
        list.selectionModel().selectFirst
      }
    }

    // whenever the text changes, update the search regex
    text.onChange { (_, _, s) =>
      search onNext Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE)
    }
  }

  // create the main menu
  val menu = new MainMenu(this)

  // simple hack to get the info box to grow
  top = new VBox(menu, info) {
    info.prefWidth <== width
  }

  // main body contains headlines
  center = list

  // search bar at the bottom
  bottom = searchField

  // hide the preview whenever a headline is not selected
  right <== when (headline =!= null) choose preview otherwise (null: Preview)
}
