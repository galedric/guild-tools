package api

import models._
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._

abstract class Event(val name: String, val arg: JsValueWrapper) {
	def asJson: JsValue = Json.obj("name" -> name, "arg" -> arg)
}

case class CalendarEventCreate(event: CalendarEvent) extends Event("event:create", event)
case class CalendarEventUpdate(event: CalendarEvent) extends Event("event:update", event)
case class CalendarEventUpdateFull(full: CalendarEventFull) extends Event("event:update:full", full)
case class CalendarEventDelete(id: Int) extends Event("event:delete", id)

case class CalendarAnswerCreate(answer: CalendarAnswer) extends Event("answer:create", answer)
case class CalendarAnswerUpdate(answer: CalendarAnswer) extends Event("answer:update", answer)
case class CalendarAnswerDelete(user: Int, event: Int) extends Event("answer:delete", Json.obj("user" -> user, "event" -> event))

case class CalendarTabCreate(tab: CalendarTab) extends Event("calendar:tab:create", tab)
case class CalendarTabUpdate(tab: CalendarTab) extends Event("calendar:tab:update", tab)
case class CalendarTabDelete(id: Int) extends Event("calendar:tab:delete", id)
case class CalendarTabWipe(id: Int) extends Event("calendar:tab:wipe", id)

case class CalendarLockAcquire(id: Int, owner: String) extends Event("calendar:lock:acquire", Json.obj("id" -> id, "owner" -> owner))
case class CalendarLockRelease(id: Int) extends Event("calendar:lock:release", id)

case class CalendarSlotUpdate(slot: CalendarSlot) extends Event("calendar:slot:update", slot)
case class CalendarSlotDelete(tab: Int, slot: Int) extends Event("calendar:slot:delete", Json.obj("tab" -> tab, "slot" -> slot))

case class SlackCreate(slack: Slack) extends Event("absence:create", slack)
case class SlackUpdate(slack: Slack) extends Event("absence:update", slack)
case class SlackDelete(id: Int) extends Event("absence:delete", id)
