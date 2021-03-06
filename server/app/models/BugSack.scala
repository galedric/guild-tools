package models

import utils.DateTime
import utils.SlickAPI._

case class BugReport(key: String, user: Int, date: DateTime, rev: String, error: String, stack: String, navigator: String)

class BugSack(tag: Tag) extends Table[BugReport](tag, "gt_bugsack") {
	def key = column[String]("key", O.PrimaryKey)
	def user = column[Int]("user")
	def date = column[DateTime]("date")
	def rev = column[String]("rev")
	def error = column[String]("error")
	def stack = column[String]("stack")
	def navigator = column[String]("navigator")

	def * = (key, user, date, rev, error, stack, navigator) <> (BugReport.tupled, BugReport.unapply)
}

object BugSack extends TableQuery(new BugSack(_))
