package models.application

import java.sql.Timestamp
import models.User
import models.mysql._

case class Application(id: Int, user: Int, date: Timestamp, stage: Int, have_posts: Boolean, updated: Timestamp)

object Applications extends TableQuery(new Applications(_)) {
	/** Filter for applications that a given user can access */
	def canAccess(user: Rep[Int], member: Rep[Boolean], promoted: Rep[Boolean])(application: Applications): Rep[Boolean] = {
		val stage = application.stage
		val valid_officer_access = (stage > Stage.Trial.id || stage === Stage.Pending.id) && promoted
		val valid_member_access = (stage > Stage.Pending.id) && (application.user === user || member)
		valid_officer_access || valid_member_access
	}

	/** Check if a user can access a given application */
	def canAccess(user: User, application: Application) = {
		if (application.stage > Stage.Trial.id || application.stage == Stage.Pending.id) user.promoted
		else if (application.stage > Stage.Pending.id) application.user == user.id || user.member
		else false
	}

	/** Fetch every open applications visible by the user and their unread status */
	val listOpen = Compiled((user_id: Rep[Int], member: Rep[Boolean], promoted: Rep[Boolean]) => {
		for {
			application <- Applications.sortBy(_.updated.desc).filter(canAccess(user_id, member, promoted))
			unread = ApplicationReadStates.isUnread.extract(application.id, user_id, member)
		} yield (application, unread)
	})

	/** Fetch every open applications visible by the user and their unread status */
	def listOpenForUser(user: User) = listOpen(user.id, user.member, user.promoted)

	/** Fetch application by id */
	val getById = Compiled((id: Rep[Int]) => {
		for (a <- Applications if a.id === id) yield a
	})

	/** Fetch application by id and check that the user can access it */
	val getByIdChecked = Compiled((id: Rep[Int], user: Rep[Int], member: Rep[Boolean], promoted: Rep[Boolean]) => {
		getById.extract(id).filter(canAccess(user, member, promoted))
	})

	/** Fetch application body data by id */
	val getData = Compiled((id: Rep[Int]) => {
		getById.extract(id).map(_.data)
	})

	/** Fetch application body data by id and check that the user can access it */
	val getDataChecked = Compiled((id: Rep[Int], user: Rep[Int], member: Rep[Boolean], promoted: Rep[Boolean]) => {
		getByIdChecked.extract(id, user, member, promoted).map(_.data)
	})

	/*
	def changeState(user: User, application: Int, stage: Int) = {
		if (stage < PENDING || stage > ARCHIVED)
			throw new IllegalArgumentException("Invalid stage")

		if (!user.promoted)
			throw new IllegalAccessException("Unpromoted user cannot change application state")

		val query = for {
			// Fetch the current application and ensure that it is not already in the requested stage
			apply <- Applications.filter(_.id === application).result.head
			_ = if (apply.stage == stage) throw new IllegalStateException("Application is already in the given stage")

			// Update the application
			_ <- Applications.filter(_.id === application).map(_.stage).update(stage)

			// Send the update message in the feed
			update_message = s"${user.name} changed the application stage from ${stageName(apply.stage)} to ${stageName(stage)}"
			_ <- postMessage(user, application, update_message, false, true)

			// Remove any read state relative to the application
			_ <- ApplicationReadStates.filter(_.apply === apply.id).delete
		} yield apply

		query
	}*/
}

class Applications(tag: Tag) extends Table[Application](tag, "gt_apply") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def user = column[Int]("user")
	def date = column[Timestamp]("date")
	def stage = column[Int]("stage")
	def data = column[String]("data")
	def have_posts = column[Boolean]("have_posts")
	def updated = column[Timestamp]("updated")

	def * = (id, user, date, stage, have_posts, updated) <> (Application.tupled, Application.unapply)
}