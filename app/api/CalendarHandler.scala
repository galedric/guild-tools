package api

import java.sql.Timestamp
import java.util.GregorianCalendar
import actors.SocketHandler
import models.{ CalendarEvents, _ }
import models.mysql._
import models.sql._
import play.api.libs.json.{ Json, JsNull, JsValue }
import utils.SmartTimestamp.Implicits._
import scala.collection.mutable
import utils.SmartTimestamp
import java.text.SimpleDateFormat
import java.text.ParseException
import scala.slick.jdbc.JdbcBackend.SessionDef
import gt.Global.ExecutionContext
import utils.scheduler
import scala.concurrent.duration._
import java.util.Date
import gt.Socket

/**
 * Shared calendar-related values
 */
object CalendarHelper {
	val guildies_groups = Set[Int](8, 9, 11)

	val guildAnswersQuery = Compiled { (event_id: Column[Int]) =>
		for {
			(u, a) <- Users leftJoin CalendarAnswers on ((u, a) => u.id === a.user && a.event === event_id)
			if (u.group inSet CalendarHelper.guildies_groups)
			c <- Chars if c.owner === u.id && c.active
		} yield (u, (a.answer.?, a.date.?, a.note, a.char), c)
	}

	val answersQuery = Compiled { (event_id: Column[Int]) =>
		for {
			a <- CalendarAnswers if a.event === event_id
			u <- Users if u.id === a.user
			c <- Chars if c.owner === u.id && c.active
		} yield (u, a, c)
	}
}

/**
 * Manages edit locks on tabs
 */
object CalendarLocksManager {
	/**
	 * Every locks currently in use
	 */
	private var locks: Map[Int, CalendarLock] = Map()

	/**
	 * Locks garbage collector
	 */
	scheduler.schedule(5.second, 5.second) {
		val now = new Date().getTime
		CalendarLocksManager.synchronized {
			locks.values.filter(now - _.lastRefresh > 15000) foreach (_.release())
		}
	}

	/**
	 * Acquire a new lock
	 */
	def acquire(tab: Int, owner: String): Option[CalendarLock] = this.synchronized {
		if (locks.contains(tab)) {
			None
		} else {
			val lock = new CalendarLock(tab, owner)
			locks += tab -> lock
			Socket !# CalendarLockAcquire(tab, owner)
			Some(lock)
		}
	}

	/**
	 * Query lock status
	 */
	def status(tab: Int): Option[String] = locks.get(tab).map(_.owner)

	/**
	 * Release a lock
	 */
	def release(lock: CalendarLock): Unit = this.synchronized {
		locks.get(lock.tab) foreach { l =>
			if (lock == l) {
				locks -= lock.tab
				Socket !# CalendarLockRelease(lock.tab)
			}
		}
	}

	/**
	 * The lock object class
	 */
	class CalendarLock private[CalendarLocksManager](val tab: Int, val owner: String) {
		/**
		 * Lock is valid until released
		 */
		var valid = true

		/**
		 * Keep last refresh time
		 */
		var lastRefresh: Long = 0

		/**
		 * Refresh the lock to prevent garbage collection
		 */
		def refresh(): Unit = { lastRefresh = new Date().getTime }
		refresh()

		/**
		 * Release this log
		 */
		def release(): Unit = {
			if (valid) {
				valid = false
				CalendarLocksManager.release(this)
			}
		}
	}
}

/**
 * Implements calendar-related API
 */
trait CalendarHandler {
	self: SocketHandler =>

	object CalendarContext {
		var event_id = -1
		var event_editable = false
		var event_disclosed = false
		var event_tabs = Set[Int]()
		var edit_lock: Option[CalendarLocksManager.CalendarLock] = None

		def resetEventContext() = {
			event_id = -1
			event_editable = false
			event_disclosed = false
			event_tabs = Set()
			edit_lock = edit_lock flatMap { lock =>
				lock.release()
				None
			}
		}

		def checkTabEditable(tab_id: Int): Boolean = {
			CalendarContext.event_editable && CalendarContext.event_tabs.contains(tab_id)
		}
	}

	/**
	 * $:calendar:load
	 */
	def handleCalendarLoad(arg: JsValue): MessageResponse = {
		val month = (arg \ "month").as[Int]
		val year = (arg \ "year").as[Int]

		val from = SmartTimestamp.createSQL(year, month - 1, 21)
		val to = SmartTimestamp.createSQL(year, month + 1, 15)

		val events = for {
			(e, a) <- CalendarEvents leftJoin CalendarAnswers on ((e, a) => a.event === e.id && a.user === user.id)
			if (e.date > from && e.date < to) && (e.visibility =!= CalendarVisibility.Private || a.answer.?.isDefined)
		} yield (e, a.answer.?)

		val events_list = DB.withSession { events.list(_) }

		var watched_events = events_list.map(_._1.id).toSet
		var potential_events = mutable.Map[Int, Event]()

		socket.bindEvents {
			// Event created
			case ev @ CalendarEventCreate(event) => {
				if (watched_events.contains(event.id)) {
					// Delayed broadcast for private events
					true
				} else if (event.date.between(from, to)) {
					// Visible event
					if (event.visibility == CalendarVisibility.Private && event.owner != user.id) {
						// Delay transmission of this event until invitation event
						potential_events += (event.id -> ev)
						false
					} else {
						// Visible event, let's go!
						watched_events += event.id
						true
					}
				} else {
					// Outside view
					false
				}
			}

			// Event updated
			case CalendarEventUpdate(event) => {
				if (potential_events.contains(event.id)) {
					potential_events(event.id) = CalendarEventCreate(event)
					false
				} else {
					watched_events.contains(event.id)
				}
			}

			// Event deletes
			case CalendarEventDelete(id) => {
				potential_events -= id
				utils.doIf(watched_events.contains(id)) { watched_events -= id }
			}

			// Answer created
			case ev @ CalendarAnswerCreate(answer) => {
				val eid = answer.event
				if (answer.user != user.id) {
					false
				} else if (potential_events.contains(eid)) {
					socket ! potential_events(eid)
					socket ! ev
					watched_events += eid
					potential_events -= eid
					false
				} else {
					watched_events.contains(eid)
				}
			}

			// Answer updated
			case CalendarAnswerUpdate(answer) => {
				answer.user == user.id && watched_events.contains(answer.event)
			}

			case CalendarAnswerDelete(answer_user, event) => {
				answer_user == user.id && watched_events.contains(event)
			}
		}

		MessageResults(events_list map {
			case (e, a) =>
				Json.obj("id" -> e.id, "event" -> e, "answer" -> (a.map(Json.toJson(_)).getOrElse(JsNull): JsValue))
		})
	}

	/**
	 * $:calendar:create
	 */
	def handleCalendarCreate(arg: JsValue): MessageResponse = {
		val title = (arg \ "title").as[String]
		val desc = (arg \ "desc").as[String]
		val visibility = (arg \ "type").as[Int]
		val hour = (arg \ "hour").as[Int]
		val minutes = (arg \ "min").as[Int]
		val dates_raw = (arg \ "dates").as[List[String]]

		def inRange(a: Int, b: Int, c: Int) = (a >= b && a <= c)

		if (!CalendarVisibility.isValid(visibility) || !inRange(hour, 0, 23) || !inRange(minutes, 0, 59)) {
			return MessageFailure("INVALID_EVENT_DATA")
		}

		if (dates_raw.length > 1) {
			if (!user.officer) return MessageFailure("MULTI_NOT_ALLOWED")
			if (visibility == CalendarVisibility.Announce) return MessageAlert("Creating announces on multiple days is not allowed.")
		}

		if (visibility != CalendarVisibility.Private && !user.officer) return MessageAlert("Members can only create restricted events.")

		val format = new SimpleDateFormat("yyyy-MM-dd")
		val now = SmartTimestamp.now

		def isValidDate(ts: SmartTimestamp): Boolean = {
			val delta = (ts - now).time / 1000
			!(delta < -86400 || delta > 5270400)
		}

		val dates = dates_raw map { date =>
			try {
				Some(SmartTimestamp.parse(date, format))
			} catch {
				case e: ParseException => None
			}
		} collect {
			case Some(ts) if isValidDate(ts) => ts
		}

		if (dates.length < 1) return MessageFailure("INVALID_DATES")

		DB.withSession { implicit s =>
			dates foreach { date =>
				val template = CalendarEvent(
					id = 0,
					title = title,
					desc = desc,
					owner = user.id,
					date = date,
					time = hour * 100 + minutes,
					`type` = visibility,
					state = 0)

				val id: Int = (CalendarEvents returning CalendarEvents.map(_.id)) += template
				CalendarEvents.notifyCreate(template.copy(id = id))

				if (visibility != CalendarVisibility.Announce) {
					// Create the default tab
					CalendarTabs += CalendarTab(0, id, "Default", None, 0, true)

					// Invite the owner into his event
					val answer = CalendarAnswer(user.id, id, now, 1, None, None)
					CalendarAnswers += answer
					CalendarAnswers.notifyCreate(answer)
				}
			}
		}

		MessageSuccess
	}

	/**
	 * $:calendar:answer
	 */
	def handleCalendarAnswer(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
		val event_id = (arg \ "event").as[Int]
		val answer = (arg \ "answer").as[Int]
		val note_raw = (arg \ "note").asOpt[String]
		val char = (arg \ "char").asOpt[Int]

		val row = for {
			(e, a) <- CalendarEvents leftJoin CalendarAnswers on ((e, a) => a.event === e.id && a.user === user.id)
			if (e.id === event_id) && (e.visibility =!= CalendarVisibility.Private || a.answer.?.isDefined)
		} yield (e, a.answer.?, a.note, a.char)

		row.firstOption map {
			case (event, old_answer, old_note, old_char) =>
				if (event.visibility == CalendarVisibility.Private && old_answer.isEmpty) return MessageFailure("UNINVITED")
				if (event.state != CalendarEventState.Open) return MessageFailure("CLOSED_EVENT")

				val note = (if (note_raw.isDefined) note_raw else old_note) filter (!_.matches("^\\s*$"))

				val template = CalendarAnswer(
					user = user.id,
					event = event_id,
					date = SmartTimestamp.now,
					answer = answer,
					note = note,
					char = if (char.isDefined) char else old_char)

				CalendarAnswers.insertOrUpdate(template)
				CalendarAnswers.notifyUpdate(template)

				MessageSuccess
		} getOrElse {
			MessageFailure("EVENT_NOT_FOUND")
		}
	}

	/**
	 * $:calendar:delete
	 */
	def handleCalendarDelete(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
		val event_id = (arg \ "id").as[Int]

		var query = CalendarEvents.filter(_.id === event_id)
		if (!user.officer) {
			query = query.filter(_.owner === user.id)
		}

		if (query.delete > 0) {
			CalendarEvents.notifyDelete(event_id)
			MessageSuccess
		} else {
			MessageFailure("EVENT_NOT_FOUND")
		}
	}

	/**
	 * $:calendar:event
	 */
	def handleCalendarEvent(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
		val event_id = (arg \ "id").as[Int]
		val event = CalendarEvents.filter(_.id === event_id).first

		if (event.visibility == CalendarVisibility.Announce) {
			return MessageFailure("OPENING_ANNOUNCE")
		}

		// Fetch answers for guild events
		def fetchGuildAnswers = {
			CalendarHelper.guildAnswersQuery(event_id).list.groupBy(_._1.id.toString).mapValues { list =>
				val user = list(0)._1

				val (a_answer, a_date, a_note, a_char) = list(0)._2
				val answer = {
					if (a_answer.isDefined) {
						val a = CalendarAnswer(
							user = user.id,
							event = event_id,
							date = a_date.get,
							answer = a_answer.get,
							note = a_note,
							char = a_char
						)
						Some(a)
					} else {
						None
					}
				}

				val chars = list.map(_._3)
				CalendarAnswerTuple(user, answer, chars)
			}
		}

		// Fetch answers for non-guild events
		def fetchEventAnswers = {
			CalendarHelper.answersQuery(event_id).list.groupBy(_._1.id.toString).mapValues { list =>
				val user = list(0)._1
				val answer = Some(list(0)._2)
				val chars = list.map(_._3)
				CalendarAnswerTuple(user, answer, chars)
			}
		}

		// Select the correct answer fetcher for this event
		val answers = {
			if (event.visibility == CalendarVisibility.Guild)
				fetchGuildAnswers
			else
				fetchEventAnswers
		}

		// Extract own answer
		val my_answer = answers.get(user.id.toString).flatMap(_.answer)

		// Check invitation in private event
		if (event.visibility == CalendarVisibility.Private && my_answer.isEmpty) return MessageFailure("NOT_INVITED")

		// Record successful event access
		CalendarContext.event_id = event.id
		CalendarContext.event_editable = (event.owner == user.id) || user.officer
		CalendarContext.event_disclosed = CalendarContext.event_editable || event.state != CalendarEventState.Open

		// Expand or conceal event
		val visible = if (CalendarContext.event_disclosed) event.expand else event.conceal

		// Extract tabs set
		CalendarContext.event_tabs = visible.tabs.map(_.id).toSet

		def expandAnswer(answer: CalendarAnswer) = {
			if (answer.event == event_id) {
				socket ! CalendarAnswerReplace(answer.expand)
			}
			false
		}

		// Event page bindings
		socket.bindEvents {
			case CalendarAnswerCreate(answer) => expandAnswer(answer)
			case CalendarAnswerUpdate(answer) => expandAnswer(answer)
			case CalendarAnswerReplace(answer) => (answer.answer.exists(_.event == event_id))

			case CalendarEventUpdate(event) => {
				if (event.id == event_id) {
					if (event.state != CalendarEventState.Open && !CalendarContext.event_disclosed) {
						CalendarContext.event_disclosed = true
						socket ! CalendarEventUpdateFull(event.expand)
						false
					} else if (event.state == CalendarEventState.Open && !CalendarContext.event_editable) {
						CalendarContext.event_disclosed = false
						socket ! CalendarEventUpdateFull(event.conceal)
						false
					} else {
						true
					}
				} else {
					false
				}
			}

			case CalendarEventUpdateFull(full) => (full.event.id == event_id)
			case CalendarEventDelete(id) => (id == event_id)

			case CalendarTabCreate(tab) => {
				if (tab.event == event_id) {
					CalendarContext.event_tabs += tab.id
					CalendarContext.event_disclosed
				} else {
					false
				}
			}

			case CalendarTabUpdate(tab) => (tab.event == event_id && CalendarContext.event_disclosed)
			case CalendarTabWipe(id) => (CalendarContext.event_tabs.contains(id) && CalendarContext.event_disclosed)

			case CalendarTabDelete(id) => {
				if (CalendarContext.event_tabs.contains(id)) {
					CalendarContext.event_tabs -= id
				}
				CalendarContext.event_disclosed
			}

			case CalendarSlotUpdate(slot) => (CalendarContext.event_disclosed && CalendarContext.event_tabs.contains(slot.tab))
			case CalendarSlotDelete(tab, _) => (CalendarContext.event_disclosed && CalendarContext.event_tabs.contains(tab))

			case CalendarLockAcquire(tab, _) => (CalendarContext.event_disclosed && CalendarContext.event_tabs.contains(tab))
			case CalendarLockRelease(tab) => (CalendarContext.event_disclosed && CalendarContext.event_tabs.contains(tab))
		} onUnbind {
			CalendarContext.resetEventContext()
		}

		MessageResults(Json.obj(
			"event" -> event,
			"answers" -> answers,
			"answer" -> my_answer,
			"tabs" -> visible.tabs,
			"slots" -> visible.slots,
			"editable" -> CalendarContext.event_editable))
	}

	/**
	 * $:calendar:event:state
	 */
	def handleCalendarEventState(arg: JsValue): MessageResponse = {
		val state = (arg \ "state").as[Int]

		if (!CalendarEventState.isValid(state)) return MessageFailure("BAD_STATE")
		if (!CalendarContext.event_editable) return MessageFailure("FORBIDDEN")

		DB.withTransaction { implicit s =>
			val query = CalendarEvents.filter(_.id === CalendarContext.event_id)
			query.map(_.state).update(state)
			CalendarEvents.notifyUpdate(query.first.copy(state = state))
		}

		MessageSuccess
	}

	/**
	 * $:calendar:event:editdesc
	 */
	def handleCalendarEventEditDesc(arg: JsValue): MessageResponse = {
		val desc = (arg \ "desc").as[String]
		if (!CalendarContext.event_editable) return MessageFailure("FORBIDDEN")

		DB.withTransaction { implicit s =>
			val query = CalendarEvents.filter(_.id === CalendarContext.event_id)
			query.map(_.desc).update(desc)
			CalendarEvents.notifyUpdate(query.first.copy(desc = desc))
		}

		MessageSuccess
	}

	/**
	 * $:calendar:comp:set
	 * $:calendar:comp:reset
	 */
	def handleCalendarCompSet(arg: JsValue, reset: Boolean = false): MessageResponse = {
		val tab = (arg \ "tab").as[Int]
		val slot = (arg \ "slot").as[Int]

		if (slot < 0 || slot > 30) return MessageFailure("BAD_SLOT")
		if (!CalendarContext.checkTabEditable(tab)) return MessageFailure("FORBIDDEN")

		if (reset) {
			DB.withSession { s =>
				CalendarSlots.filter(s => s.tab === tab && s.slot === slot).delete(s)
				CalendarSlots.notifyDelete(tab, slot)
			}
		} else {
			val owner = (arg \ "char" \ "owner").as[Int]
			val name = (arg \ "char" \ "name").as[String]
			val clazz = (arg \ "char" \ "class").as[Int]
			val role = (arg \ "char" \ "role").as[String]

			val template = CalendarSlot(tab, slot, owner, name, clazz, role)

			DB.withSession { implicit s =>
				CalendarSlots.filter(s => s.tab === tab && s.owner === owner).delete
				CalendarSlots.insertOrUpdate(template)
				CalendarSlots.notifyUpdate(template)
			}
		}

		MessageSuccess
	}

	/**
	 * $:calendar:tab:create
	 */
	def handleCalendarTabCreate(arg: JsValue): MessageResponse = {
		val title = (arg \ "title").as[String]

		if (!CalendarContext.event_editable) return MessageFailure("FORBIDDEN")

		val event = CalendarContext.event_id

		DB.withTransaction { implicit s =>
			val max_order = CalendarTabs.filter(_.event === event).map(_.order).max.run.get
			val template = CalendarTab(0, event, title, None, max_order + 1, false)
			val id: Int = (CalendarTabs returning CalendarTabs.map(_.id)) += template
			CalendarTabs.notifyCreate(template.copy(id = id))
		}

		MessageSuccess
	}

	/**
	 * $:calendar:tab:delete
	 */
	def handleCalendarTabDelete(arg: JsValue): MessageResponse = {
		val tab_id = (arg \ "id").as[Int]
		if (!CalendarContext.checkTabEditable(tab_id)) return MessageFailure("FORBIDDEN")

		DB.withSession { implicit s =>
			if (CalendarTabs.filter(t => t.id === tab_id && !t.locked).delete > 0) {
				CalendarTabs.notifyDelete(tab_id)
			}
		}

		MessageSuccess
	}

	/**
	 * $:calendar:tab:swap
	 */
	def handleCalendarTabSwap(arg: JsValue): MessageResponse = {
		val tab1_id = (arg \ "a").as[Int]
		val tab2_id = (arg \ "b").as[Int]

		if (!CalendarContext.checkTabEditable(tab1_id)) return MessageFailure("FORBIDDEN")
		if (!CalendarContext.checkTabEditable(tab2_id)) return MessageFailure("FORBIDDEN")

		DB.withTransaction { implicit s =>
			val tab1 = CalendarTabs.filter(_.id === tab1_id).first
			val tab2 = CalendarTabs.filter(_.id === tab2_id).first

			val tab1_new = tab1.copy(order = tab2.order)
			val tab2_new = tab2.copy(order = tab1.order)

			CalendarTabs.filter(_.id === tab1_id).map(_.order).update(tab2.order)
			CalendarTabs.filter(_.id === tab2_id).map(_.order).update(tab1.order)

			CalendarTabs.notifyUpdate(tab1_new)
			CalendarTabs.notifyUpdate(tab2_new)
		}

		MessageSuccess
	}

	/**
	 * $:calendar:tab:rename
	 */
	def handleCalendarTabRename(arg: JsValue): MessageResponse = {
		val tab_id = (arg \ "id").as[Int]
		val title = (arg \ "title").as[String]

		if (!CalendarContext.checkTabEditable(tab_id)) return MessageFailure("FORBIDDEN")

		DB.withSession { implicit s =>
			val tab_query = CalendarTabs.filter(_.id === tab_id)
			tab_query.map(_.title).update(title)
			CalendarTabs.notifyUpdate(tab_query.first)
		}

		MessageSuccess
	}

	/**
	 * $:calendar:tab:wipe
	 */
	def handleCalendarTabWipe(arg: JsValue): MessageResponse = {
		val tab_id = (arg \ "id").as[Int]
		if (!CalendarContext.checkTabEditable(tab_id)) return MessageFailure("FORBIDDEN")

		DB.withSession { implicit s =>
			if (CalendarSlots.filter(_.tab === tab_id).delete > 0) {
				CalendarTabs.notifyWipe(tab_id)
			}
		}

		MessageSuccess
	}

	/**
	 * $:calendar:tab:edit
	 */
	def handleCalendarTabEdit(arg: JsValue): MessageResponse = {
		val tab_id = (arg \ "id").as[Int]
		val note = (arg \ "note").asOpt[String]
		if (!CalendarContext.checkTabEditable(tab_id)) return MessageFailure("FORBIDDEN")

		DB.withSession { implicit s =>
			val tab_query = CalendarTabs.filter(_.id === tab_id)
			tab_query.map(_.note).update(note)
			CalendarTabs.notifyUpdate(tab_query.first)
		}

		MessageSuccess
	}

	/**
	 * $:calendar:lock:status
	 */
	def handleCalendarLockStatus(arg: JsValue): MessageResponse = {
		val tab_id = (arg \ "id").as[Int]
		if (!CalendarContext.checkTabEditable(tab_id)) return MessageFailure("FORBIDDEN")
		MessageResults(Json.obj("owner" -> CalendarLocksManager.status(tab_id)))
	}

	/**
	 * $:calendar:lock:acquire
	 */
	def handleCalendarLockAcquire(arg: JsValue): MessageResponse = {
		val tab_id = (arg \ "id").as[Int]
		if (!CalendarContext.checkTabEditable(tab_id)) return MessageFailure("FORBIDDEN")

		CalendarLocksManager.acquire(tab_id, user.name) map { lock =>
			CalendarContext.edit_lock = Some(lock)
			MessageSuccess
		} getOrElse {
			MessageAlert("An error occurred while acquiring the edit lock for this tab")
		}
	}

	/**
	 * $:calendar:lock:refresh
	 */
	def handleCalendarLockRefresh(): MessageResponse = {
		CalendarContext.edit_lock foreach (_.refresh())
		MessageSuccess
	}

	/**
	 * $:calendar:lock:release
	 */
	def handleCalendarLockRelease(): MessageResponse = {
		CalendarContext.edit_lock foreach (_.release())
		MessageSuccess
	}
}
