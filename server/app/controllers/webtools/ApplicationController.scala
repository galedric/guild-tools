package controllers.webtools

import controllers.webtools.WtController.{Deny, UserRequest}
import models.Toons
import models.application._
import play.api.libs.json.Json
import play.api.mvc._
import reactive.ExecutionContext
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import utils.SlickAPI._

class ApplicationController extends Controller with WtController {
	/**
	  * Creates an ApplicationAction, optionally ignoring the failure to fetch the application data.
	  * Only the last application for the user, if it exists, is fetched and given to the underlying action.
	  * If no application can be fetched and `ignore` is not set, the user will be redirected to /wt/application.
	  * If `ignore` is set, the underlying action will be executed but the application parameter will be null.
	  * @param ignore   whether to ignore failure
	  * @param action   the original action to execute
	  * @return         an Action to be executed by Play
	  */
	private def ApplicationActionIgnore(ignore: Boolean)(action: (UserRequest[AnyContent], Application) => Future[Result]): Action[AnyContent] = {
		UserAction.async { req =>
			if (req.chars.isEmpty) throw Deny
			Applications.lastForUser(req.user.id).result.headOption.run flatMap {
				case Some(a) => action(req, a)
				case None if ignore => action(req, null)
				case _ => Future.successful(Redirect("/wt/application"))
			}
		}
	}

	/**
	  * Creates an ApplicationAction based on a asynchronous actions.
	  */
	private def ApplicationActionAsync(action: (UserRequest[AnyContent], Application) => Future[Result]): Action[AnyContent] = {
		ApplicationActionIgnore(false)(action)
	}

	/**
	  * Creates an ApplicationAction providing application data in addition to the UserRequest object.
	  * ApplicationAction also check that the user has at least one char registered with his account.
	  */
	private def ApplicationAction(action: (UserRequest[AnyContent], Application) => Result): Action[AnyContent] = {
		ApplicationActionIgnore(false) { case (r, a) => Future.successful(action(r, a)) }
	}

	/**
	  * Dispatches the user to the correct stage page.
	  * - If the user is a member, redirect to /wt/application/member (dummy page for members)
	  * - If there is no application, redirect to step 2 if charter is read, else to step1
	  * - If there is an application, redirect to the corresponding state
	  * - If the session data contain the "ignore" key, act as if no application were available
	  */
	def dispatch = ApplicationActionIgnore(true) { case (req, application) =>
		Future.successful {
			Redirect {
				(if (req.session.data.contains("ignore")) null else application) match {
					case _ if req.user.member && req.cookies.get("ignore_member").isEmpty => "/wt/application/member"

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

	/**
	  * Guild charter stage.
	  * If this action is called with a ?validate GET parameter, the `charter` key is added to the current
	  * session and the user is redirected to step 2.
	  */
	def step1 = UserAction { req =>
		if (req.chars.isEmpty) throw Deny
		if (req.getQueryString("validate").isDefined) {
			Redirect("/wt/application/step2").withSession(req.session + ("charter" -> "1"))
		} else {
			Ok(views.html.wt.application.step1.render(req))
		}
	}

	/**
	  * Application form.
	  */
	def step2 = UserAction { req =>
		if (req.chars.isEmpty) throw Deny
		req.session.data.contains("charter") match {
			case false => Redirect("/wt/application/step1")
			case true =>
				val main = req.chars.find(_.main).getOrElse(throw Deny)
				val alts = req.chars.filter(c => c.active && !c.main)
				Ok(views.html.wt.application.step2.render(main, alts, req))
		}
	}

	/**
	  * The user submitted his application.
	  */
	def submit = UserAction.async { req =>
		// Ensure that the user can submit an application
		if (req.chars.isEmpty) throw Deny
		if (!req.session.data.contains("charter")) throw Deny

		def error(msg: String) = Future.successful(Ok(msg))

		// Save application data
		req.body.asFormUrlEncoded.flatMap(_.get("data")).flatMap(_.headOption) match {
			case None => error("Une erreur est survenue lors de la lecture des données de postulation.")
			case Some(data) =>
				Try { Json.parse(data) } match {
					case Failure(_) => error("Une erreur est survenue lors de la validation des données de postulation.")
					case Success(_) => for (_ <- Applications.create(req.user, DataType.JsonData, data)) yield {
						Ok("OK").withSession(req.session - "charter")
					}
				}
		}
	}

	/**
	  * Application is in Pending stage.
	  */
	def step3 = ApplicationAction {
		case (_, application) if application == null || application.stage != Stage.Pending.id => Redirect("/wt/application")
		case (req, _) => Ok(views.html.wt.application.step3.render(req))
	}

	/**
	  * Application is in Review stage.
	  */
	def step4 = ApplicationActionAsync {
		case (_, application) if application == null || application.stage != Stage.Review.id =>
			Future.successful(Redirect("/wt/application"))

		case (req, application) =>
			val query = for {
				(message, main) <- ApplicationFeed join Toons on { case (m, c) => m.user === c.owner && c.main }
				if message.apply === application.id && message.secret === false
			} yield (message, main)

			for (posts <- query.sortBy(_._1.date.asc).run) yield {
				Ok(views.html.wt.application.step4.render(posts, req))
			}
	}

	/**
	  * Applicant is in trial.
	  */
	def step5 = ApplicationAction {
		case (_, application) if application == null || application.stage != Stage.Trial.id => Redirect("/wt/application")
		case (req, _) => Ok(views.html.wt.application.step5.render(req))
	}

	/**
	  * Archived application.
	  * Used for Refused, Accepted and Archived stages.
	  */
	def step6 = ApplicationAction {
		case (_, application) if application == null || application.stage < Stage.Refused.id => Redirect("/wt/application")
		case (req, _) => Ok(views.html.wt.application.step6.render(req))
	}

	/**
	  * User is a guild member. Display placeholder page.
	  */
	def member = UserAction { req =>
		Ok(views.html.wt.application.member.render(req))
	}

	/**
	  * Only outputs the guild charter for inclusion into WordPress.
	  */
	def charter = Action {
		Ok(views.html.wt.application.charter.render(false))
	}
}
