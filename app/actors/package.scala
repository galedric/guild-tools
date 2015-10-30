import akka.actor._
import gtp3.Socket.{Disconnect, ForceStop, OutgoingFrame}
import play.api.Logger
import play.api.libs.concurrent.Akka
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.reflect.ClassTag
import play.api.Play.current

/**
 * The actor package contains static services actors.
 * Static actors are used when a common service is required across the application and
 * concurrency needs to be handled properly.
 */
package object actors {
	/**
	 * Constructs persistent TypedActors.
	 */
	private[actors] abstract class StaticActor[A <: AnyRef, B <: A : ClassTag](name: String, timeout: FiniteDuration = 1.minute) {
		private[actors] val $actor: A = TypedActor(Akka.system).typedActorOf(TypedProps[B].withTimeout(timeout), name)
	}

	/**
	 * Extract the inner TypedActor from the companion object.
	 */
	@inline implicit def invokeTypedActor[A <: AnyRef](s: StaticActor[A, _]): A = s.$actor

	/**
	 * DeadLetters logger.
	 */
	private class DeadLetterLogger extends Actor {
		def receive = {
			case DeadLetter(msg, from, to) => msg match {
				case OutgoingFrame(_) => from ! ForceStop
				case Disconnect => // noop
				case _ => Logger.warn(s"DeadLetter from $from to $to -> $msg")
			}
		}
	}

	// Subscribe the logger to the event stream
	private val DeadLetterLogger = Akka.system.actorOf(Props[DeadLetterLogger], "DeadlettersLogger")
	Akka.system.eventStream.subscribe(DeadLetterLogger, classOf[DeadLetter])

	/**
	 * A tagged ActorRef.
	 * It is fully equivalent to an ActorRef, the type parameter only serve documenting purpose.
	 */
	type ActorTag[+T] = ActorRef
}
