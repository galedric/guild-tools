package gt.component.dashboard

import gt.component.{GtHandler, Tab, View}
import util.jsannotation.js
import xuen.Component

object GtDashboard extends Component[GtDashboard](
	selector = "gt-dashboard",
	templateUrl = "/assets/imports/dashboard.html"
) with View {
	val module = "dashboard"
	val tabs: TabGenerator = (selector, path, user) => Seq(
		Tab("Dashboard", "/dashboard", active = true)
	)
}

@js class GtDashboard extends GtHandler {

}

