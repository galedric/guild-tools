package gt

import gt.components.View
import org.scalajs.dom.{PopStateEvent, document, window}
import scala.scalajs.js.JSStringOps._
import scala.scalajs.js.RegExp
import utils.EventSource

object Router {
	private[this] var locked = false
	private[this] var activePath: String = null

	val onUpdate = new EventSource[(String, Seq[(String, String)], View)]

	def start(): Unit = {
		window.addEventListener("popstate", (e: PopStateEvent) => update())
		update()
	}

	def update(): Unit = {
		val path = window.location.pathname
		if (path == activePath) return

		if (path.`match`(RegExp("^/.*/$")) != null) {
			goto(path.jsSlice(0, -1))
			return
		}

		// Search matching view
		for (route <- Routes.definitions if route.view.validate(App.user)) {
			for (args <- route.matches(path)) {
				activePath = path
				onUpdate.emit((path, args, route.view))
				return
			}
		}

		// Fallback
		if (path == "/dashboard") throw new IllegalStateException()
		else goto("/dashboard", true)
	}

	def goto(path: String, replace: Boolean = false): Unit = if (!locked) {
		if (path.matches("^https?://.*")) {
			document.location.href = path
		} else {
			if (replace) window.history.replaceState(null, null, path)
			else window.history.pushState(null, null, path)
			update()
		}
	}

	def lock(): Unit = { locked = true }
	def unlock(): Unit = { locked = false }
}
