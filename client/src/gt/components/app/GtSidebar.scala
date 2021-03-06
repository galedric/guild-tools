package gt.components.app

import gt.components.GtHandler
import gt.{App, Router}
import rx.Var
import scala.scalajs.js
import utils.annotation.data
import utils.jsannotation.js
import xuen.Component

/**
  * The application sidebar.
  */
object GtSidebar extends Component[GtSidebar](
	selector = "gt-sidebar",
	templateUrl = "/assets/imports/app.html"
)

@js class GtSidebar extends GtHandler {
	/** Sidebar item definition */
	@data case class Icon(icon: String, key: String, link: String, hidden: Boolean = false)

	// Sidebar items
	val icons = js.Array(
		Icon("widgets", "dashboard", "/dashboard"),
		Icon("account_circle", "profile", "/profile"),
		//Icon("mail", "messages", "/messages"),
		Icon("today", "calendar", "/calendar", hidden = !App.user.fs),
		Icon("pages", "composer", "/composer", hidden = !App.user.promoted),
		Icon("supervisor_account", "roster", "/roster", hidden = !App.user.fs),
		//Icon("assignment_ind", "apply", if (app.user.roster) "/apply" else "/apply-guest"),
		Icon("airplay", "streams", "/streams", hidden = !App.user.fs)
	).filter(!_.hidden)

	// Tracks the module ID of the current view
	val module = Var(null: String)
	Router.onUpdate ~> { case (_, _, view) => module := view.module }

	/** Navigates to the requested path */
	def navigate(path: String): Unit = Router.goto(path)
}
