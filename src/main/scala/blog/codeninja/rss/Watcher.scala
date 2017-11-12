package blog.codeninja.rss

import java.nio.file._
import java.util.concurrent.TimeUnit
import scala.concurrent._

class Watcher(file: Path)(onChange: () => Unit) {
  import ExecutionContext.Implicits.global
  
  /**
   * Create a new service to watch a directory for changes.
   */
  private val service = FileSystems.getDefault.newWatchService
  
  /**
   * Register the service on the parent directory.
   */
  file.getParent.register(service, StandardWatchEventKinds.ENTRY_MODIFY)
  
  /**
   * Whenever a file changes, if it's the file in question, call the
   * handler function.
   */
  def onEvent(e: WatchEvent[_]): Unit = e.context match {
    case p: Path if p.getFileName == file.getFileName => onChange()
    case _                                            => ()
  }

  /**
   * Set to true when the watcher thread should terminate.
   */
  private var cancelRequested = false
  
  /**
   * Set the request to true.
   */
  def cancel: Unit = cancelRequested = true
 
  /**
   * Start the watch thread.
   */
  def start = Future {
    while (!cancelRequested) {
      val key = Option(service.poll(500, TimeUnit.MILLISECONDS))
      
      // loop over all the events posted looking for modifications
      key foreach { key =>
        key.pollEvents.toArray collect {
          case e: WatchEvent[_] => onEvent(e)
        }

        // prepare the next event
        key.reset
      }
    }
  }
}