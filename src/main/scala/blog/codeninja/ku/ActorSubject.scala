package blog.codeninja.ku

import monix.execution._
import monix.execution.atomic._
import monix.reactive.observers._
import monix.reactive.subjects._
import scala.concurrent._
import scala.util._

object ActorSubject {
  sealed trait Status[T]

  final case class Ok[T](value: T) extends Status[T]
  final case class Complete[T]() extends Status[T]
  final case class Error[T](ex: Throwable) extends Status[T]

  case class State[T](
    val subscribers: List[Subscriber[T]] = List.empty,
    val status: Status[T] = Complete(),
  )
}

abstract class ActorSubject[M,T](initialValue: T) extends Subject[M,T] {
  import ActorSubject._

  // create the initial state with an initial value
  val state = Atomic(new State[T](status = Ok(initialValue)))

  // how the actor's internal state value is updated
  def receive(oldValue: T, message: M): T = ???

  // get the number of subscribers
  def size = state.get.subscribers.length

  // process a message to the actor
  def onNext(message: M): Future[Ack] = {
    val curState = state.get

    curState.status match {
      case Ok(value) =>
        Try(receive(value, message)) match {
          case Failure(ex)       => onError(ex); Ack.Stop
          case Success(newValue) =>
            val ns = curState.subscribers flatMap { s =>
              s onNext newValue match {
                case Ack.Continue => Some(s)
                case Ack.Stop     => None
              }
            }

            // create the new state object for the subject
            state update new State[T](ns, Ok(newValue))

            // keep processing
            Ack.Continue
        }
      case _ =>
        Ack.Stop
    }
  }

  // the actor is complete
  def onComplete = {
    state.get.subscribers foreach (_.onComplete)

    // set the status to complete, no more messages will be processed
    state transform (_.copy(status = ActorSubject.Complete[T]()))
  }

  // there was an error while processing a message
  def onError(ex: Throwable) = {
    state.get.subscribers foreach (_ onError ex)

    // set the status to complete, no more messages will be processed
    state transform (_.copy(status = ActorSubject.Error[T](ex)))
  }

  // add a new subscriber to the subject
  def unsafeSubscribeFn(subscriber: Subscriber[T]) = {
    val curState = state.get

    curState.status match {
      case Complete() =>
        subscriber.onComplete
      case Error(ex) =>
        subscriber.onError(ex)
      case Ok(x) =>
        val newState = curState.copy(subscribers = subscriber :: curState.subscribers)

        // add the new subscriber
        if (state.compareAndSet(curState, newState)) {
          subscriber.onNext(x)
        } else {
          unsafeSubscribeFn(subscriber)
        }
    }

    // nothing to cancel
    Cancelable.empty
  }
}
