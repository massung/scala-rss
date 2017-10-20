package blog.codeninja.ku

import monix.reactive._
import monix.execution._
import scala.concurrent.Future
import scalafx.application.JFXApp
import scalafx.scene.Scene
import scalafx.scene.image.Image
import scalafx.scene.layout.BorderPane

object Ku extends JFXApp {
  import Scheduler.Implicits.global

  // create a new aggregator whenever the preferences change
  val aggregator = Config.prefs map { prefs =>
    val agg = new Aggregator(prefs)

    Config.prefs subscribe new Observer[Config.Prefs] {
      def onError(ex: Throwable) = agg.cancel
      def onComplete = agg.cancel

      // When the prefs change again, cancel the aggregator because
      // a new one will be made. Also, stop watching for a new prefs
      // update, because we'll make a new observer at that time.
      def onNext(prefs: Config.Prefs): Future[Ack] = {
        agg.cancel; Ack.Stop
      }
    }

    // return the aggregator
    agg
  }

  // create the primary stage
  stage = new JFXApp.PrimaryStage {
    title = "Ku"
    minWidth = 560

    scene = new Scene {
      root = new View(aggregator)
    }

    // stop all background processing
    onCloseRequest = { _ =>
      Archive.onComplete
      Config.cancel
    }

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
