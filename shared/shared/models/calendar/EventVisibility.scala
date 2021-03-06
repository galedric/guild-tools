package models.calendar

import models.User

/**
  * Visibility of a calendar event
  */
object EventVisibility {
	final val Roster = 1
	final val Public = 2
	final val Restricted = 3
	final val Announce = 4
	final val Guild = 5

	def isValid(v: Int) = v > 0 && v < 6

	def name(visibility: Int): String = visibility match {
		case EventVisibility.Announce => "Announce"
		case EventVisibility.Guild => "Guild event"
		case EventVisibility.Public => "Public event"
		case EventVisibility.Restricted => "Restricted event"
		case EventVisibility.Roster => "Roster event"
		case _ => "Event"
	}

	def canCreate(visibility: Int, user: User): Boolean = visibility match {
		case Roster | Announce => user.promoted
		case Public => user.member
		case Guild => user.roster
		case Restricted => true
		case _ => false
	}
}
