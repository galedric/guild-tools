package gt.components.calendar

import gt.components.GtHandler
import gt.services.RosterService
import models.Toon
import rx.Const
import utils.jsannotation.js
import xuen.Component

object CalendarUnitFrame extends Component[CalendarUnitFrame](
	selector = "calendar-unit-frame",
	templateUrl = "/assets/imports/views/calendar-event.html",
	dependencies = Seq()
)

@js class CalendarUnitFrame extends GtHandler {
	val roster = service(RosterService)

	val toon = property[Int]

	val data = toon ~! { id =>
		if (id > 0) roster.toon(id)
		else Const(Toon.Dummy)
	}

	def hasToon = toon > 0
}
