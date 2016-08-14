package gt.component.dashboard

import gt.component.GtHandler
import gt.component.widget.{GtAlert, GtBox}
import gt.service.NewsFeedService
import rx.syntax.MonadicOps
import rx.{Rx, Var}
import util.annotation.data
import util.jsannotation.js
import xuen.Component

object DashboardNews extends Component[DashboardNews](
	selector = "dashboard-news",
	templateUrl = "/assets/imports/views/dashboard.html",
	dependencies = Seq(GtBox, GtAlert, DashboardNewsFilter)
)

@js class DashboardNews extends GtHandler {
	val newsfeed = service(NewsFeedService)
	val available = newsfeed.channel.open

	val news = Var[Seq[Unit]](Nil)
	val count = Rx { news.length }

	@data object sources {
		val mmo = Var(true)
		val blue = Var(true)
		val wow = Var(true)

		val foo = for {
			mmo <- mmo
			blue <- blue
			wow <- wow
		} yield (mmo, blue, wow)

		foo ~> { v => println(v) }
	}
}
