package models

import java.sql.Timestamp
import models.mysql._
import utils.{PubSub, SmartTimestamp}

// ============================================================================

case class Apply(id: Int, user: Int, date: Timestamp, stage: Int, have_posts: Boolean, updated: Timestamp) {
	require(stage >= Applys.PENDING && stage <= Applys.ARCHIVED)
}

object Applys extends TableQuery(new Applys(_)) with PubSub[User] {
	// Stages
	final val PENDING = 0
	final val REVIEW = 1
	final val TRIAL = 2
	final val REFUSED = 3
	final val ACCEPTED = 4
	final val ARCHIVED = 5

	// Stage id -> names
	def stageName(stage: Int) = stage match {
		case PENDING => "Pending"
		case REVIEW => "Review"
		case TRIAL => "Trial"
		case REFUSED => "Refused"
		case ACCEPTED => "Accepted"
		case ARCHIVED => "Archived"
		case _ => "Unknown"
	}

	// Events
	case class UnreadUpdated(apply: Int, unread: Boolean)
	case class ApplyUpdated(apply: Apply)

	// Fetch every open applications visible by the user
	val openForUser = Compiled((user: Rep[Int], wide: Rep[Boolean]) => {
		val default_read = SmartTimestamp(2000, 1, 1).toSQL
		for {
			apply <- Applys.sortBy(_.updated.desc) if apply.stage < Applys.REFUSED && (wide || apply.user === user)
			read = ApplyReadStates.filter(r => r.apply === apply.id && r.user === user).map(_.date).max.getOrElse(default_read)
		} yield (apply, read < apply.updated)
	})

	// Fetch data for a specific application
	val applyById = Compiled((id: Rep[Int]) => {
		for (apply <- Applys if apply.id === id) yield apply
	})

	// Update the read state flag
	def markAsRead(id: Int, user: User): Unit = {
		ApplyReadStates insertOrUpdate ApplyReadState(user.id, id, SmartTimestamp.now)
		this.publish(UnreadUpdated(id, false), u => u.id == user.id)
	}
}

class Applys(tag: Tag) extends Table[Apply](tag, "gt_apply") {
	def id = column[Int]("id", O.PrimaryKey)
	def user = column[Int]("user")
	def date = column[Timestamp]("date")
	def stage = column[Int]("stage")
	def data = column[String]("data")
	def have_posts = column[Boolean]("have_posts")
	def updated = column[Timestamp]("updated")

	def * = (id, user, date, stage, have_posts, updated) <> (Apply.tupled, Apply.unapply)
}

// ============================================================================

case class ApplyFeedMessage(id: Int, apply: Int, user: Int, date: Timestamp, text: String, secret: Boolean, system: Boolean)

object ApplyFeed extends TableQuery(new ApplyFeed(_)) {
	val forApply = Compiled((apply: Rep[Int], with_secret: Rep[Boolean]) => {
		ApplyFeed.filter(m => m.apply === apply && (!m.secret || with_secret)).sortBy(m => m.date.asc)
	})
}

class ApplyFeed(tag: Tag) extends Table[ApplyFeedMessage](tag, "gt_apply_feed") {
	def id = column[Int]("id", O.PrimaryKey)
	def apply = column[Int]("apply")
	def user = column[Int]("user")
	def date = column[Timestamp]("date")
	def text = column[String]("text")
	def secret = column[Boolean]("secret")
	def system = column[Boolean]("system")

	def * = (id, apply, user, date, text, secret, system) <> (ApplyFeedMessage.tupled, ApplyFeedMessage.unapply)
}

// ============================================================================

case class ApplyReadState(user: Int, apply: Int, date: Timestamp)

object ApplyReadStates extends TableQuery(new ApplyReadStates(_))

class ApplyReadStates(tag: Tag) extends Table[ApplyReadState](tag, "gt_apply_read") {
	def user = column[Int]("user", O.PrimaryKey)
	def apply = column[Int]("apply", O.PrimaryKey)
	def date = column[Timestamp]("date")

	def * = (user, apply, date) <> (ApplyReadState.tupled, ApplyReadState.unapply)
}

