import java.sql.Timestamp
import java.text.SimpleDateFormat
import scala.language.implicitConversions
import play.api.Play.current
import play.api.libs.json._

package object models {
	val DB = play.api.db.slick.DB
	val mysql = scala.slick.driver.MySQLDriver.simple
	val sql = scala.slick.jdbc.StaticQuery

	implicit object timestampFormat extends Format[Timestamp] {
		val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
		def reads(json: JsValue) = JsSuccess(new Timestamp(format.parse(json.as[String]).getTime))
		def writes(ts: Timestamp) = JsString(format.format(ts))
	}

	implicit val userJsonWriter = new Writes[User] {
		def writes(user: User): JsValue = {
			Json.obj(
				"id" -> user.id,
				"name" -> user.name,
				"group" -> user.group,
				"color" -> user.color,
				"officer" -> user.officer,
				"developer" -> user.developer)
		}
	}

	implicit val charJsonFormat = Json.format[Char]
	implicit val eventJsonFormat = Json.format[CalendarEvent]
	implicit val answerJsonFormat = Json.format[CalendarAnswer]
	implicit val tabJsonFormat = Json.format[CalendarTab]
	implicit val slotJsonFormat = Json.format[CalendarSlot]
	implicit val eventFullJsonFormat = Json.format[CalendarEventFull]
	implicit val feedJsonFormat = Json.format[Feed]
	implicit val absenceJsonFormat = Json.format[Slack]
}
