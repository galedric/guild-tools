package controllers.webtools

import controllers.WebTools
import controllers.WebTools.{Deny, UserRequest}
import models._
import models.application.{Application, Applications, Stage}
import models.mysql._
import play.api.mvc.{Action, AnyContent, Result}
import reactive.ExecutionContext
import scala.concurrent.Future

trait ApplicationController {
	this: WebTools =>

	def ApplicationAction(ignore: Boolean)(action: (UserRequest[AnyContent], Application) => Future[Result]): Action[AnyContent] = {
		val FutureRedirect = Future.successful(Redirect("/wt/application"))
		UserAction.async { req =>
			if (req.chars.isEmpty) throw Deny
			Applications.lastForUser(req.user.id).result.headOption.run flatMap {
				case Some(a) => action(req, a)
				case None if ignore => action(req, null)
				case _ => FutureRedirect
			}
		}
	}

	def ApplicationAction(action: (UserRequest[AnyContent], Application) => Future[Result]): Action[AnyContent] = {
		ApplicationAction(false)(action)
	}

	def dispatch = ApplicationAction(true) { case (req, application) =>
		Future.successful {
			Redirect {
				(if (req.session.data.contains("ignore")) null else application) match {
					//case _ if req.user.member => "/wt/application/step6"

					case null if req.session.data.contains("charter") => "/wt/application/step2"
					case null => "/wt/application/step1"

					case a if a.stage > Stage.Trial.id => "/wt/application/step6"
					case a if a.stage == Stage.Trial.id => "/wt/application/step5"
					case a if a.stage == Stage.Review.id => "/wt/application/step4"
					case a if a.stage == Stage.Pending.id => "/wt/application/step3"
				}
			}
		}
	}

	def step1 = UserAction { req =>
		if (req.chars.isEmpty) throw Deny
		if (req.getQueryString("validate").isDefined) {
			Redirect("/wt/application/step2").withSession(req.session + ("charter" -> "1"))
		} else {
			Ok(views.html.wt.application.step1.render(req))
		}
	}

	def step2 = UserAction { req =>
		if (req.chars.isEmpty) throw Deny
		Ok(views.html.wt.application.step2.render(req))
	}

	/** User is a guild member */
	def roster = UserAction { req => Ok(views.html.wt.application.roster.render(req)) }

	/**
	  * Only outputs the guild charter for inclusion into WordPress
	  */
	def charter = Action {Ok(views.html.wt.application.charter.render(false))}
}
