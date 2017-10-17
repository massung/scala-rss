package blog.codeninja.ku

import scalafx.beans.property.ObjectProperty
import scalafx.scene.control.{Menu, MenuBar, MenuItem, SeparatorMenuItem}

class MainMenu(val headline: ObjectProperty[Headline]) extends MenuBar {
  val quitItem = new MenuItem("Quit") {
    onAction = { _ => }
  }

  // mark the current headline as read
  val archiveItem = new MenuItem("Archive") {
    onAction = { _ => }

    // only enabled when there's a headline selected
    disable <== headline === null
  }

  val prefsItem = new MenuItem("Preferences...") {
    onAction = { _ => Config.open }
  }

  val aboutItem = new MenuItem("About") {
    onAction = { _ => }
  }

  //
  val fileMenu = new Menu("File") {
    items = Seq(quitItem)
  }

  val editMenu = new Menu("Edit") {
    items = Seq(archiveItem, new SeparatorMenuItem, prefsItem)
  }

  val helpMenu = new Menu("Help") {
    items = Seq(aboutItem)
  }

  //
  menus = Seq(fileMenu, editMenu, helpMenu)
}
