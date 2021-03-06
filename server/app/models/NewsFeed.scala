package models

import utils.DateTime
import utils.SlickAPI._

class NewsFeed(tag: Tag) extends Table[NewsFeedData](tag, "gt_feed") {
	def guid = column[String]("guid", O.PrimaryKey)
	def source = column[String]("source")
	def title = column[String]("title")
	def link = column[String]("link")
	def time = column[DateTime]("time")
	def tags = column[String]("tags")

	def * = (guid, source, title, link, time, tags) <> ((NewsFeedData.apply _).tupled, NewsFeedData.unapply)
}

object NewsFeed extends TableQuery(new NewsFeed(_))
