package actors

import scala.concurrent.duration._
import akka.actor.{TypedActor, TypedProps}
import api._
import gt.Socket
import models._
import models.mysql._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.json._
import utils.LazyCache

trait RosterManagerInterface {
	def users: Map[Int, User]
	def chars: Map[Int, Char]

	def user(id: Int): Option[User]
	def char(id: Int): Option[Char]

	def compositeRoster: JsObject
	def compositeUser(id: Int): JsObject

	def updateChar(char: Char): Unit
	def deleteChar(id: Int): Unit
}

object RosterManagerActor {
	val RosterManager: RosterManagerInterface = TypedActor(Akka.system).typedActorOf(TypedProps[RosterManagerActor](), name = "RosterManager")
}

class RosterManagerActor extends RosterManagerInterface {
	/**
	 * List of every users
	 */
	val roster_users = LazyCache(1.minute) {
		DB.withSession { implicit s =>
			Users.filter(_.group inSet AuthHelper.allowedGroups).list.map(u => u.id -> u).toMap
		}
	}

	/**
	 * List of every chars
	 */
	val roster_chars = LazyCache(1.minute) {
		DB.withSession { implicit s =>
			Chars.list.map(c => c.id -> c).toMap
		}
	}

	/**
	 * The composite (users ++ chars)
	 */
	def roster_composite = LazyCache(1.minute) {
		Json.obj("users" -> roster_users.values.toList, "chars" -> roster_chars.values.toList)
	}

	def users: Map[Int, User] = roster_users
	def chars: Map[Int, Char] = roster_chars

	def user(id: Int): Option[User] = roster_users.get(id)
	def char(id: Int): Option[Char] = roster_chars.get(id)

	def compositeRoster: JsObject = roster_composite

	def compositeUser(id: Int): JsObject = {
		val chars = roster_chars.collect({ case (_, char) if char.owner == id => char }).toSet
		Json.obj("user" -> roster_users.get(id), "chars" -> chars)
	}

	def updateChar(char: Char): Unit = {
		if (roster_chars.contains(char.id))
			roster_chars := (_.updated(char.id, char))
		else
			roster_chars := (_ + (char.id -> char))
		roster_composite.clear()
		Socket ! Message("roster:char:update", char)
	}

	def deleteChar(id: Int): Unit = {
		roster_chars := roster_chars - id
		roster_composite.clear()
		Socket ! Message("roster:char:delete", id)
	}
}
