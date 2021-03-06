package gt.components.widget

import facades.dom4.HTMLElement
import gt.components.GtHandler
import gt.components.widget.floating.{AbstractFloating, FloatingUtils, GtFloatingPlaceholder}
import org.scalajs.dom.{Event, MouseEvent, window}
import scala.scalajs.js
import utils.jsannotation.js
import xuen.Component

object GtTooltip extends Component[GtTooltip](
	selector = "gt-tooltip",
	templateUrl = "/assets/imports/floating.html"
)

@js class GtTooltip extends GtHandler with AbstractFloating {
	val visible = attribute[Boolean] := false
	val width = (attribute[Int] := 300) ~>> (w => style.maxWidth = w + "px")
	val passive = attribute[Boolean] := false

	var parent: HTMLElement = null
	var transition: Boolean = false
	var placeholder: GtFloatingPlaceholder = null

	val enterListener: js.Function1[MouseEvent, Unit] = show _
	val leaveListener: js.Function1[Event, Unit] = hide _
	val moveListener: js.Function1[MouseEvent, Unit] = move _

	override def attached(): Unit = if (!transition) {
		parent = FloatingUtils.parent(this)
		if (parent != null && !passive) {
			parent.addEventListener("mouseenter", enterListener)
			parent.addEventListener("mouseleave", leaveListener)
		}
	}

	override def detached(): Unit = if (!transition) {
		if (parent != null) {
			parent.removeEventListener("mouseenter", enterListener)
			parent.removeEventListener("mouseleave", leaveListener)
			parent = null
		}
	}

	def show(e: MouseEvent): Unit = if (!visible) {
		FloatingUtils.lift(this, hide)
		parent.addEventListener("mousemove", moveListener)
		visible := true
		move(e)
		fire("tooltip-show")
	}

	def hide(e: Event = null): Unit = if (visible) {
		parent.removeEventListener("mousemove", moveListener)
		FloatingUtils.unlift(this)
		visible := false
		fire("tooltip-hide")
	}

	def move(e: MouseEvent): Unit = if (visible) {
		var x = e.clientX + 10
		var y = window.innerHeight - e.clientY + 10

		if (x + offsetWidth + 20 > window.innerWidth) x -= offsetWidth + 20
		if (y + offsetHeight + 20 > window.innerHeight) y -= offsetHeight + 20

		style.left = x + "px"
		style.bottom = y + "px"
	}
}
