import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.TimeZone
import models.application.{Application, ApplicationMessage}
import models.calendar._
import play.api.Play
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.implicitConversions
import slick.dbio.{DBIOAction, NoStream}
import slick.driver.JdbcProfile
import slick.lifted.Query
import utils.SmartTimestamp

package object models {
	val DB = DatabaseConfigProvider.get[JdbcProfile](Play.current).db
	val mysql = slick.driver.MySQLDriver.api

	implicit class QueryExecutor[A](val q: Query[_, A, Seq]) extends AnyVal {
		import mysql._
		@inline def run = DB.run(q.result)
		@inline def head = DB.run(q.result.head)
		@inline def headOption = DB.run(q.result.headOption)
	}

	implicit class DBIOActionExecutor[R](val q: DBIOAction[R, NoStream, Nothing]) extends AnyVal {
		@inline def run = DB.run(q)
		@inline def await = DB.run(q).await
	}

	implicit class AwaitableFuture[A](val f: Future[A]) extends AnyVal {
		@inline def await: A = await(30.seconds)
		@inline def await(limit: Duration): A = Await.result(f, limit)
	}

	implicit val timestampFormat = new Format[Timestamp] {
		val format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
		format.setTimeZone(TimeZone.getTimeZone("UTC"))
		def reads(json: JsValue) = JsSuccess(new Timestamp(format.parse(json.as[String]).getTime))
		def writes(ts: Timestamp) = Json.obj("$date" -> format.format(ts))
	}

	implicit val smartTimestampFormat = new Format[SmartTimestamp] {
		val format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
		format.setTimeZone(TimeZone.getTimeZone("UTC"))
		def reads(json: JsValue) = JsSuccess(SmartTimestamp(format.parse(json.as[String]).getTime))
		def writes(ts: SmartTimestamp) = Json.obj("$date" -> format.format(ts))
	}

	implicit val userJsonWriter = new Writes[User] {
		def writes(user: User): JsValue = {
			Json.obj(
				"id" -> user.id,
				"name" -> user.name,
				"group" -> user.group,
				"officer" -> user.officer,
				"promoted" -> user.promoted,
				"developer" -> user.developer,
				"member" -> user.member,
				"roster" -> user.roster,
				"fs" -> user.fs)
		}
	}

	implicit val applyJsonFormat = Json.format[Application]
	implicit val applyFeedMessageJsonFormat = Json.format[ApplicationMessage]
	implicit val charJsonFormat = Json.format[Char]
	implicit val eventJsonFormat = Json.format[Event]
	implicit val answerJsonFormat = Json.format[Answer]
	implicit val tabJsonFormat = Json.format[Tab]
	implicit val slotJsonFormat = Json.format[Slot]
	implicit val eventFullJsonFormat = Json.format[EventFull]
	implicit val feedJsonFormat = Json.format[Feed]
	implicit val absenceJsonFormat = Json.format[Slack]
	implicit val chatMessageFormat = Json.format[ChatMessage]
	implicit val chatWhisperFormat = Json.format[ChatWhisper]
	implicit val composerLockoutFormat = Json.format[ComposerLockout]
	implicit val composerGroupFormat = Json.format[ComposerGroup]
	implicit val composerSlotFormat = Json.format[ComposerSlot]
	implicit val streamJsonFormat = Json.format[live.Stream]
	implicit val profileJsonFormat = Json.format[Profile]
}
