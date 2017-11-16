package blog.codeninja.rss

import monix.execution._
import monix.reactive._
import monix.reactive.observers._
import monix.reactive.subjects._
import scala.concurrent.Future

sealed trait ArchiveAction

final case class Push(h: Headline) extends ArchiveAction
final case class Undo() extends ArchiveAction

class Archive extends Observer[ArchiveAction] {
  val actor = PublishSubject[ArchiveAction]()
  
  def onNext(action: ArchiveAction) = actor.onNext(action)
  def onComplete = actor.onComplete
  def onError(ex: Throwable) = actor.onError(ex)
  
  val observable = List.empty[Headline] +: actor.scan(List.empty[Headline]) {
    (t, a) => a match {
      case Push(h) => h :: t
      case Undo()  => t drop 1
    }
  }
}

object Archive {
  import scala.language.higherKinds
  import scala.language.implicitConversions
  
  implicit def toObservable[List[Headline]](a: Archive) = a.observable 
}