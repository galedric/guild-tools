package gt.component.calendar

import gt.component.GtHandler
import gt.component.widget.GtBox
import gt.component.widget.form.{GtCheckbox, GtForm, GtInput}
import gt.service.CalendarService
import model.calendar.{Answer, AnswerValue, Event}
import rx.Var
import util.Microtask
import util.jsannotation.js
import xuen.Component

object CalendarEventReply extends Component[CalendarEventReply](
	selector = "calendar-event-reply",
	templateUrl = "/assets/imports/views/calendar-event.html",
	dependencies = Seq(GtBox, GtForm, GtCheckbox, GtInput)
)

@js class CalendarEventReply extends GtHandler {
	val calendar = service(CalendarService)

	val event = property[Event]
	val answer = property[Answer]

	answer ~> { a =>
		status := a.answer
		toon := a.toon.getOrElse(0)
		note := a.note.orNull
	}

	val status = Var[Int](AnswerValue.Pending)
	val toon = Var[Int](0)
	val note = Var[String]("")

	def update(): Unit = Microtask.schedule {
		if (status > 0) {
			calendar.changeEventAnswer(event.id, status, Option(toon.!).filter(_ > 0), Option(note.!).filter(_.trim.nonEmpty))
		}
	}
}
