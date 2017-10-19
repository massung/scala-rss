package blog.codeninja.ku

import monix.execution.Scheduler.Implicits.global
import scalafx.application.JFXApp
import scalafx.scene.Scene
import scalafx.scene.image.Image
import scalafx.scene.layout.BorderPane

object Ku extends JFXApp {
  val agg = Config.prefs map {
    prefs => new Aggregator(prefs.urls: _*)
  }

  // each time the aggregator changes, cancel the old one
  val canceler = agg foreach {
    old => Config.prefs foreach (_ => old.cancel)
  }

  /*
  val agg = new Aggregator(
    "http://digg.com/rss/top.rss",
    "http://www.engadget.com/rss.xml",
    "http://feeds.feedburner.com/NewshourHeadlines",
    "https://www.techrepublic.com/rssfeeds/articles/",
    "http://prospect.org/rss.xml",
    "http://www.npr.org/rss/rss.php?id=1001",
    "http://www.newyorker.com/feed/news",
    "https://www.theguardian.com/us/rss",
  )
  */

  // create the primary stage
  stage = new JFXApp.PrimaryStage {
    title = "Ku"
    minWidth = 560

    scene = new Scene {
      root = new View(agg)
    }

    // stop all background processing
    onCloseRequest = { _ =>
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
