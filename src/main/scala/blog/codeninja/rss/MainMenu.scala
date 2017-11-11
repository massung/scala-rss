package blog.codeninja.rss

import scalafx.scene.control.{Menu, MenuBar, MenuItem, SeparatorMenuItem}
import scalafx.scene.input.KeyCombination

class MainMenu(val view: View) extends MenuBar {
  val quitItem = new MenuItem("Quit") {
    onAction = { _ => RSS.quit }
  }

  val openItem = new MenuItem("Open in Browser...") {
    onAction = { _ => view.open }
    accelerator = KeyCombination.keyCombination("enter")
    disable <== view.headline === null
  }

  val archiveItem = new MenuItem("Archive") {
    onAction = { _ => view.archive() }
    accelerator = KeyCombination.keyCombination("x")
    disable <== view.headline === null
  }

  val undoArchiveItem = new MenuItem("Undo Archive") {
    onAction = { _ => view.undoArchive() }
    accelerator = KeyCombination.keyCombination("u")
    disable <== view.headline === null
  }

  val prefsItem = new MenuItem("Preferences...") {
    onAction = { _ => Config.open }
  }

  val findItem = new MenuItem("Find") {
    onAction = { _ => view.doSearch }
    accelerator = KeyCombination.keyCombination("/")
  }

  val clearItem = new MenuItem("Clear") {
    onAction = { _ => view.clear }
    accelerator = KeyCombination.keyCombination("esc")
  }

  val copyItem = new MenuItem("Copy Link") {
    onAction = { _ => view.copy(false) }
    accelerator = KeyCombination.keyCombination("c")
    disable <== view.headline === null
  }

  val aboutItem = new MenuItem("About") {
    onAction = { _ => }
  }

  val fileMenu = new Menu("File") {
    items = Seq(openItem, new SeparatorMenuItem,quitItem)
  }

  val editMenu = new Menu("Edit") {
    items = Seq(
      copyItem,
      new SeparatorMenuItem,
      findItem,
      clearItem,
      new SeparatorMenuItem,
      archiveItem,
      undoArchiveItem,
      new SeparatorMenuItem,
      prefsItem,
    )
  }

  val viewMenu = new Menu("View") {
  }

  val helpMenu = new Menu("Help") {
    items = Seq(aboutItem)
  }

  menus = Seq(fileMenu, editMenu, viewMenu, helpMenu)
}
