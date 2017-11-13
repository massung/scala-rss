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

  /**
   * Every time the preferences are updated, cancel the current download
   * tasks and create a new Aggregator.
   */
  val aggregator = Config.prefs.scan(new Aggregator(new Config.Prefs)) {
    (agg, prefs) => agg.cancel; new Aggregator(prefs)
  }

  /**
   * Elegantly shutdown the preferences watch, close out the archive, and
   * terminate the application.
   */
  override def stopApp = {
    Config.watcher.cancel
    Archive.onComplete
    Platform.exit
  }

  /**
   * Create the primary stage.
   */
  stage = new JFXApp.PrimaryStage {
    title = "Scala RSS Reader"
    minWidth = 560

    scene = new Scene {
      root = new View(aggregator)
    }

    icons.setAll(
      new Image("/icon/icon_128.png"),
      new Image("/icon/icon_64.png"),
      new Image("/icon/icon_48.png"),
      new Image("/icon/icon_32.png"),
      new Image("/icon/icon_24.png"),
      new Image("/icon/icon_20.png"),
      new Image("/icon/icon_16.png"),
    )

    // stop all background processing
    onCloseRequest = { _ => JFXApp.Stage.close }
  }

  // load the configuration file
  Config.load
}
