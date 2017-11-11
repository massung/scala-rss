package blog.codeninja.rss

import monix.execution._
import monix.reactive.observers._
import monix.reactive.subjects._
import scala.concurrent.Future

sealed trait ArchiveAction

// enumerated archive actions to take
final case class Push(h: Headline) extends ArchiveAction
final case class Undo() extends ArchiveAction

object Archive extends Subject[ArchiveAction, List[Headline]] {
  import atomic.Atomic

  // all the headlines that make up the archive
  private var headlines: List[Headline] = List.empty
  private var finalEvent: Either[Throwable, Boolean] = Right(false)

  // maintain a list of all subscribers
  val subscribers = Atomic(List[Subscriber[List[Headline]]]())

  // add a new headline to the archive
  def onNext(action: ArchiveAction): Future[Ack] = {
    val list = subscribers.get

    if (finalEvent contains false) {
      action match {
        case Push(h) => headlines = h :: headlines
        case Undo()  => headlines = headlines drop 1
      }

      // send the updated list of headlines to all subscribers
      val update = for (subscriber <- list) yield {
        subscriber onNext headlines match {
          case Ack.Continue => Some(subscriber)
          case Ack.Stop     => None
        }
      }

      // exchange the list of subscribers
      subscribers.compareAndSet(list, update.flatten)

      Ack.Continue
    } else {
      Ack.Stop
    }
  }

  // finish the archive
  def onComplete = {
    for (subscriber <- subscribers.get) {
      subscriber.onComplete
    }

    // terminate the observer
    finalEvent = Right(true)

    // TODO: write the archive to disk
  }

  // shouldn't ever happen
  def onError(ex: Throwable) = {
    for (subscriber <- subscribers.get) {
      subscriber.onError(ex)
    }

    // terminate the observer
    finalEvent = Left(ex)
  }

  // get the number of subscribers
  def size = subscribers.get.size

  // add a new subscriber to emit the archive to
  def unsafeSubscribeFn(subscriber: Subscriber[List[Headline]]): Cancelable = {
    finalEvent match {
      case Right(true) => subscriber.onComplete
      case Left(ex) => subscriber.onError(ex)
      case _ =>
        val list = subscribers.get
        val update = subscriber :: list

        // add the new subscriber
        if (subscribers.compareAndSet(list, update)) {
          subscriber onNext headlines
        } else {
          unsafeSubscribeFn(subscriber)
        }
    }

    // nothing to cancel
    Cancelable.empty
  }
}
