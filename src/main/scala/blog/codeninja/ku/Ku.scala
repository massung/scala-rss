package blog.codeninja.ku

import scalafx.application.JFXApp
import scalafx.scene.Scene
import scalafx.scene.layout.BorderPane

object Ku extends JFXApp {
  val agg = new Aggregator(
    "http://digg.com/rss/top.rss",
    "http://www.engadget.com/rss.xml",
    "http://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml",
  )

  // create the primary stage
  stage = new JFXApp.PrimaryStage {
    title = "Ku"
    minWidth = 470

    scene = new Scene {
      root = new View(agg)
    }
  }

  // load the icons for the language
  stage.icons.setAll()
}
