package blog.codeninja.ku

import scala.collection.mutable
import scalafx.scene.image.{Image, ImageView}

object ImageCache {
  val cache: mutable.HashMap[String, Image] = mutable.HashMap()

  // download an image in the background
  def apply(url: String): Image =
    cache.getOrElseUpdate(url, new Image(url, true))

  // create an ImageView control for a cached image
  def view(url: String)(body: => Unit): ImageView =
    new ImageView(apply(url)) {
      body
    }
}
