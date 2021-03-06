package actors

import actors.BattleNet.BnetFailure
import actors.RosterService.{ToonDeleted, ToonUpdated}
import data.{Spec, UserGroups}
import models.{PhpBBUsers, Toon, Toons, User}
import reactive.ExecutionContext
import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.duration._
import utils.Implicits._
import utils.SlickAPI._
import utils.{DateTime, _}

private[actors] class RosterServiceImpl extends RosterService

object RosterService extends StaticActor[RosterService, RosterServiceImpl]("RosterService") {
	case class ToonUpdated(toon: Toon)
	case class ToonDeleted(toon: Toon)
}

trait RosterService extends PubSub[User] {
	// Cache of in-roster users
	private val users = CacheCell.async[Map[Int, User]](1.minute) {
		val query = for {
			user <- PhpBBUsers if user.group inSet UserGroups.fromscratch
		} yield user.id -> user
		query.run.map(s => s.toMap)
	}

	// Cache of out-of-roster users
	private val outroster_users = Cache.async[Int, User](15.minutes) { id =>
		PhpBBUsers.filter(_.id === id).head
	}

	// List of chars for a roster member
	val roster_toons = CacheCell.async[Map[Int, Seq[Toon]]](1.minutes) {
		val users = for (user <- PhpBBUsers if user.group inSet UserGroups.fromscratch) yield user.id
		val chars = for (char <- Toons.sortBy(c => (c.main.desc, c.active.desc, c.level.desc, c.ilvl.desc)) if char.owner.in(users)) yield char
		chars.run.map(_.groupBy(_.owner))
	}

	// List of chars for a specific user
	private val user_chars = Cache.async[Int, Seq[Toon]](1.minutes) { owner =>
		Toons.filter(_.owner === owner).sortBy(c => (c.main.desc, c.active.desc, c.level.desc, c.ilvl.desc)).run
	}

	// List of pending Battle.net update
	// Two b.net update on the same char at the same time will produce the same shared future
	// resolved at a later time with the same char
	private var inflightUpdates = Map[Int, Future[Toon]]()

	// Request a list of user from the roster
	def roster_users: Future[Iterable[User]] = for (u <- users.value) yield u.values

	// Request user informations
	// This function query both the full roster cache or the out-of-roster generator
	def user(id: Int): Future[User] = users.value.map(m => m(id)) recoverWith { case _ => outroster_users(id) }

	// Request a list of chars for a specific owner
	def toons(owner: Int): Future[Seq[Toon]] = roster_toons.value.map(c => c(owner)) recoverWith { case _ => user_chars(owner) }

	def toonOwner(toon: Int): Future[Int] = Toons.filter(_.id === toon).map(_.owner).head

	// Construct a request for a char with a given id.
	// If user is defined, also ensure that the char is owned by the user
	private def getOwnChar(id: Int, user: Option[User]) = {
		val query = for (c <- Toons if c.id === id) yield c
		user.map(u => query.filter(_.owner === u.id)).getOrElse(query)
	}

	// Notify subscriber that a char has been updated
	// Also clear the local cache for the owner of that char
	private def notifyUpdate(toon: Toon): Toon = {
		user_chars.clear(toon.owner)
		roster_toons.clear()
		this !# ToonUpdated(toon)
		toon
	}

	// Fetch a character from Battle.net and update its cached value in DB
	// TODO: refactor
	def refreshToon(id: Int, user: Option[User] = None): Future[Toon] = {
		// Ensure we dont start two update at the same time
		if (inflightUpdates.contains(id)) inflightUpdates(id)
		else {
			// Request for the updated char
			val char = getOwnChar(id, user)
			val res =
				char.filter(c => c.last_update < Platform.currentTime - (1000 * 60 * 15)).head
					.otherwise("Cannot refresh character at this time")
					.flatMap { oc =>
						BattleNet.fetchToon(oc.server, oc.name).recoverWith {
							case BnetFailure(response) if response.status == 404 =>
								char.map(_.failures).head.map { f =>
									val failures = f + 1
									val invalid = failures >= 3
									char.map {
										c => (c.active, c.failures, c.invalid, c.last_update)
									}.update {
										(oc.active && (!invalid || oc.main), failures, invalid, Platform.currentTime)
									}
								}.flatMap { query =>
									query.run.flatMap(_ => StacklessException("Battle.net Error: " + response.body))
								}

							// Another error occurred, just update the last_update, but don't count as failure
							case cause =>
								char.map(c => c.last_update).update(DateTime.now.timestamp).run
									.flatMap(_ => StacklessException("Error while updating character", cause))
						}.map { nc =>
							char.map {
								c => (c.klass, c.race, c.gender, c.level, c.achievements, c.thumbnail, c.ilvl, c.failures, c.invalid, c.last_update)
							}.update {
								(nc.classid, nc.race, nc.gender, nc.level, nc.achievements, nc.thumbnail, math.max(nc.ilvl, oc.ilvl), 0, false, DateTime.now.timestamp)
							}
						}.flatMap {
							query => query.run
						}.flatMap {
							_ => char.head
						}
					}

			res andThen {
				case _ => inflightUpdates -= id
			} recoverWith {
				case _ => char.head
			} foreach {
				updated => notifyUpdate(updated)
			}

			inflightUpdates += id -> res
			res
		}
	}

	// Promote a new char as the main for the user
	def promoteToon(id: Int, user: Option[User] = None): Future[(Toon, Toon)] = DB.run {
		// Construct the update for a specific char
		def update_char(id: Int, main: Boolean) =
		Toons.filter(char => char.id === id && char.main === !main).map(_.main).update(main)

		val query = for {
		// Fetch new and old main chars
			new_main <- getOwnChar(id, user).result.head
			old_main <- Toons.filter(char => char.main === true && char.owner === new_main.owner).result.head

			// Update them
			new_updated <- update_char(new_main.id, true)
			old_updated <- update_char(old_main.id, false)

			// Ensure we have updated two chars
			_ = if (new_updated + old_updated != 2) throw StacklessException("Failed to update chars")
		} yield {
			(notifyUpdate(new_main.copy(main = true)), notifyUpdate(old_main.copy(main = false)))
		}

		query.transactionally
	}

	// Common database query for enableChar() and disableChar()
	private def changeEnabledState(id: Int, user: Option[User], state: Boolean): Future[Toon] = DB.run {
		val char_query = getOwnChar(id, user).filter(_.main === false)
		for {
			_ <- char_query.map(_.active).update(state)
			char <- char_query.result.head
		} yield {
			this.notifyUpdate(char)
		}
	}

	// Update the enabled state of a character
	def enableToon(id: Int, user: Option[User] = None): Future[Toon] = changeEnabledState(id, user, true)
	def disableToon(id: Int, user: Option[User] = None): Future[Toon] = changeEnabledState(id, user, false)

	/**
	  * Changes a toon's specialization
	  *
	  * @param id the toon's ID
	  * @param spec the specialization ID
	  * @param user if set, the toon's owner must match the given user
	  */
	def changeSpec(id: Int, spec: Int, user: Option[User] = None): Future[Toon] = {
		val toonQuery = getOwnChar(id, user)
		for {
			oldToon <- toonQuery.head
			_ = if (oldToon.classid != Spec.fromId(spec).clss.id) throw new Exception("Invalid specialization for class")
			newToon <- (for {
				_ <- toonQuery.filter(_.spec =!= spec).map(_.spec).update(spec)
				toon <- toonQuery.result.head
			} yield {
				this.notifyUpdate(toon)
			}).run
		} yield newToon
	}

	// Remove an existing character from the database
	def removeToon(id: Int, user: Option[User] = None): Future[Toon] = DB.run {
		for {
			char <- getOwnChar(id, user).filter(c => c.main === false && c.active === false).result.head
			count <- Toons.filter(c => c.id === char.id).delete
			_ = if (count < 1) throw StacklessException("Failed to delete this character")
		} yield {
			user_chars.clear(char.owner)
			roster_toons.clear()
			this !# ToonDeleted(char)
			char
		}
	}

	// Add a new character for a specific user
	def registerChar(server: String, name: String, owner: Int, spec: Option[Int]): Future[Toon] = {
		for {
			toon <- BattleNet.fetchToon(server, name)
			res <- registerChar(toon, owner, spec)
		} yield {
			res
		}
	}

	def registerChar(char: Toon, owner: Int, spec: Option[Int]): Future[Toon] = DB.run {
		val count_main = Toons.filter(c => c.owner === owner && c.main === true).size.result
		val query = for {
		// Count the number of main for this user
			pre_count <- count_main
			// Construct the char for insertion with correct role, owner and main flag
			insert_char = char.copy(specid = spec.getOrElse(char.specid), owner = owner, main = pre_count < 1)
			// Insert this char
			insert_id <- (Toons returning Toons.map(_.id)) += insert_char
			// Ensure we have exactly one main for this user now
			post_count <- count_main
		} yield {
			if (post_count != 1) throw new Exception("Failed to register new character")
			val final_char = insert_char.copy(id = insert_id)
			this.notifyUpdate(final_char)
			final_char
		}
		query.transactionally
	}
}
