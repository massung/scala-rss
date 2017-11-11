package blog.codeninja.rss

import monix.reactive._
import monix.execution._
import scala.concurrent.Future
import scalafx.application.{JFXApp, Platform}
import scalafx.scene.Scene
import scalafx.scene.image.Image
import scalafx.scene.layout.BorderPane
import scalafx.stage.WindowEvent

object RSS extends JFXApp {
  import Scheduler.Implicits.global
  
  val aggregator = Config.prefs.scan(new Aggregator(Config.Prefs())) {
    (agg, prefs) => agg.cancel; new Aggregator(prefs)
  }

  // shut everything down nicely and terminate the app
  def quit = {
    Config.cancel
    Archive.onComplete
    Platform.exit
  }

  // create the primary stage
  stage = new JFXApp.PrimaryStage {
    title = "Scala RSS Reader"
    minWidth = 560

    scene = new Scene {
      root = new View(aggregator)
    }

    // stop all background processing
    onCloseRequest = { _ => quit }

    // load the config file
    Config.load
  }

  // load the icons for the language
  stage.icons.setAll(
    new Image("/icon/icon_128.png"),
    new Image("/icon/icon_64.png"),
    new Image("/icon/icon_48.png"),
    new Image("/icon/icon_32.png"),
    new Image("/icon/icon_24.png"),
    new Image("/icon/icon_20.png"),
    new Image("/icon/icon_16.png"),
  )
}
