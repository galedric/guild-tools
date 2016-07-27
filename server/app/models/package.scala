import com.google.inject.Inject
import models.application.{Application, ApplicationMessage}
import models.calendar._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.implicitConversions
import slick.dbio.{DBIOAction, NoStream}
import slick.driver.JdbcProfile
import slick.lifted.Query

package object models {
	val pkg = this
	@Inject var dbc: DatabaseConfigProvider = null

	lazy val mysql = slick.driver.MySQLDriver.api
	lazy val DB = dbc.get[JdbcProfile].db

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
