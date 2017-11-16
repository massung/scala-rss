package blog.codeninja.rss

import monix.execution._
import monix.reactive._
import monix.reactive.observers._
import monix.reactive.subjects._

/**
 * The archive is a state subject. It can have headlines pushed onto it,
 * or popped (undone). Each action updates the state of the observable.
 */
class Archive extends Observer[Archive.Action] {
  private val actor = PublishSubject[Archive.Action]()
  
  // Observer contract methods.
  def onNext(action: Archive.Action) = actor.onNext(action)
  def onComplete = actor.onComplete
  def onError(ex: Throwable) = actor.onError(ex)
  
  /**
   * Collect all the actions and update the state.
   */
  private val observable = List.empty[Headline] +: actor.scan(List.empty[Headline]) {
    (t, a) => a match {
      case Archive.Push(h) => h :: t
      case Archive.Undo()  => t drop 1
    }
  }
}

/**
 * Implicit conversion from Archive to Observable.
 */
object Archive {
  import scala.language.higherKinds
  import scala.language.implicitConversions
  
  /**
   * Actions taken on an archive must extend this trait.
   */
  sealed trait Action
  
  /**
   * Push a headline into the archive.
   */
  final case class Push(h: Headline) extends Action
  
  /**
   * Pop the last headline pushed onto the archive.
   */
  final case class Undo() extends Action
  
  /**
   * Convert an Archive into an Observable.
   */
  implicit def toObservable[List[Headline]](a: Archive) = a.observable 
}