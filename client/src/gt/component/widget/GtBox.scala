package gt.component.widget

import gt.component.GtHandler
import util.jsannotation.js
import xuen.Component

object GtBox extends Component[GtBox](
	selector = "gt-box",
	templateUrl = "/assets/imports/box.html"
)

@js class GtBox extends GtHandler {
	val heading = attribute[String]
	val scrollable = attribute[Boolean]
	val dark = attribute[Boolean]

	val hasHeading = heading ~ { _ != null }
}
