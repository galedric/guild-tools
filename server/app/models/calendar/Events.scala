package models.calendar

import models.User
import reactive.ExecutionContext
import scala.concurrent.Future
import scala.util.Success
import slick.lifted.Case
import utils.SlickAPI._
import utils.{DateTime, PubSub}

class Events(tag: Tag) extends Table[Event](tag, "gt_events_visible") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def title = column[String]("title")
	def desc = column[String]("desc")
	def owner = column[Int]("owner")
	def date = column[DateTime]("date")
	def time = column[Int]("time")
	def visibility = column[Int]("type")
	def state = column[Int]("state")
	def garbage = column[Boolean]("garbage")

	def * = (id, title, desc, owner, date, time, visibility, state) <> ((Event.apply _).tupled, Event.unapply)
}

object Events extends TableQuery(new Events(_)) with PubSub[User] {
	case class Updated(event: Event)
	case class Deleted(event: Int)

	/**
	  * Checks if a user can access an event.
	  * Pure scala version.
	  *
	  * @param user   The user to test
	  * @param event  The event
	  * @param answer The answer of this user for this event
	  * @return Whether the user is allowed to access the event
	  */
	def canAccess(user: User, event: Event, answer: Option[Answer] = None): Boolean = {
		event.visibility match {
			case EventVisibility.Announce => user.fs
			case EventVisibility.Guild => user.fs
			case EventVisibility.Public => true
			case EventVisibility.Roster => user.roster
			case EventVisibility.Restricted => answer.isDefined
			case _ => false
		}
	}

	/**
	  * Checks if a user can access an event.
	  * SQL version. Can be used as a .filter()
	  *
	  * @param user  The user to test
	  * @param event The event
	  * @return Whether the user is allowed to access the event
	  */
	def canAccess(user: User)(event: Events): Rep[Boolean] = {
		val answer = Answers.findForEventAndUser(event.id, user.id).exists
		Case
			.If(event.visibility === EventVisibility.Announce).Then(user.fs)
			.If(event.visibility === EventVisibility.Guild).Then(user.fs)
			.If(event.visibility === EventVisibility.Public).Then(true)
			.If(event.visibility === EventVisibility.Roster).Then(user.roster)
			.If(event.visibility === EventVisibility.Restricted).Then(answer)
			.Else(false)
	}

	/**
	  * Checks if a user can access an event.
	  * This version takes an event id instead of an event object.
	  *
	  * @param user The user to test
	  * @param id   The event id
	  * @return Whether the user is allowed to access the event
	  */
	def canAccess(user: User, id: Int): Rep[Boolean] = {
		Events.findById(id).filter(canAccess(user)).exists
	}

	/**
	  * Checks if a user is allowed to edit an event.
	  * A user is allowed to edit an event if they are the event owner or if they are promoted,
	  * either globally (devs, officers) or specifically for this event.
	  *
	  * @param user  The user to test
	  * @param event The event
	  * @return Whether the user is allowed to edit the event
	  */
	def canEdit(user: User)(event: Events): Rep[Boolean] = {
		val promoted = Answers.findForEventAndUser(event.id, user.id).filter(_.promote).exists
		event.owner === user.id || user.promoted || promoted
	}

	/**
	  * Runs an action if the given event is accessible by the user.
	  *
	  * @param user The user to test
	  * @param id   The event id
	  */
	def ifAccessible[T](user: User, id: Int)(action: => T): Future[T] = {
		for (_ <- Events.findById(id).filter(canAccess(user)).head) yield action
	}

	/**
	  * Runs an action if the given event is editable by the user.
	  *
	  * @param user The user to test
	  * @param id   The event's ID
	  */
	def ifEditable[T](user: User, id: Int)(action: => T): Future[T] = {
		for (_ <- Events.findById(id).filter(canEdit(user)).head) yield action
	}

	/**
	  * Finds an event by ID.
	  *
	  * @param id The event's ID
	  * @return A Query for this event
	  */
	def findById(id: Rep[Int]) = {
		Events.filter(_.id === id)
	}

	/**
	  * Finds events between two dates.
	  *
	  * @param from The lower-bound date
	  * @param to   The upper-bound date
	  * @return A Query for events between the two dates
	  */
	def findBetween(from: Rep[DateTime], to: Rep[DateTime]) = {
		Events.filter(_.date.between(from, to))
	}

	/**
	  * Changes the state (Open, Close, Canceled) of an event.
	  *
	  * @param event_id The event id
	  * @param state    The new event state
	  */
	def changeState(event_id: Int, state: Int) = {
		require(EventState.isValid(state))
		for (n <- Events.findById(event_id).filter(_.state =!= state).map(_.state).update(state).run if n > 0) {
			publishUpdate(event_id)
		}
	}

	def create(event: Event): Unit = {
		def insertEvent(event: Event) = Events.returning(Events.map(_.id)).into((a, id) => a.copy(id = id)) += event
		if (event.isAnnounce) {
			for (e <- insertEvent(event).run) publishUpdate(e.id)
		} else {
			(for {
				e <- insertEvent(event)
				answer = Answer(e.owner, e.id, DateTime.now, Answer.Accepted, None, None, true)
				a <- Answers += answer
			} yield (e.id, answer)).transactionally.run andThen {
				case Success((id, answer)) =>
					Answers.publishUpdate(answer)
					publishUpdate(id)
			}
		}
	}

	def delete(event: Int): Unit = {
		for (n <- Events.findById(event).delete.run if n > 0) {
			publish(Deleted(event))
		}
	}

	/**
	  * Publishes an updated message to PubSub subscribers.
	  *
	  * @param event_id The calendar event ID
	  * @param message  An optional constructor for the message object.
	  *                 Defaults to Events.Updated.apply
	  */
	def publishUpdate(event_id: Int, message: (Event) => Any = Updated.apply) = {
		val queries = for {
			e <- Events.findById(event_id).result.head
			a <- Answers.findForEvent(event_id).result
		} yield (e, a)

		for ((event, raw_answers) <- queries.run) {
			val answers = raw_answers.map(a => (a.user, a)).toMap
			val dispatch_message = message(event)
			this.publish(dispatch_message, u => canAccess(u, event, answers.get(u.id)))
		}
	}
}
