package controllers

import actors._
import controllers.webtools._
import models._
import models.mysql._
import play.api.mvc._
import reactive._
import scala.concurrent.Future
import scala.concurrent.duration._
import utils.Cache

class WebTools extends Controller with Wishlist {
	// The wrapper request with user informations and session token
	class UserRequest[A](val user: User, val token: String, val set_cookie: Boolean, val request: Request[A]) extends WrappedRequest[A](request)

	// Indicate that the user is correctly authenticated but doesn't have access to the requested page
	object Deny extends Exception

	// Indicate that the authentication failed
	object AuthFailed extends Exception

	// The ActionBuilder for WebTools actions
	object UserAction extends ActionBuilder[UserRequest] {
		// A phpBB session
		case class PhpBBSession(id: String, user: Int, ip: String, browser: String)

		// phpbb_sessions table definition
		class PhpBBSessions(tag: Tag) extends Table[PhpBBSession](tag, "phpbb_sessions") {
			def id = column[String]("session_id", O.PrimaryKey)
			def user = column[Int]("session_user_id")
			def ip = column[String]("session_ip")
			def browser = column[String]("session_browser")

			def * = (id, user, ip, browser) <> (PhpBBSession.tupled, PhpBBSession.unapply)
		}

		// TableQuery for PhpBB sessions
		object PhpBBSessions extends TableQuery(new PhpBBSessions(_))

		// Get User for a given GT session token
		def userForToken(token: String) = {
			for {
				session <- Sessions.filter(_.token === token).result.head
				user <- Users.filter(_.id === session.user).result.head
			} yield user
		}

		// Cache of users for session tokens
		val sessionCache = Cache.async[String, User](1.minute)(t => DB.run(userForToken(t)))

		// Attempt to get User from session cookie
		def cookieUser[A](implicit request: Request[A]) = {
			for {
				token <- Future(request.cookies.get("gt_session").get.value)
				user <- sessionCache(token)
			} yield (user, token, false)
		}

		// Attempt to create a session from a PhpBB session cookie
		def ssoUser[A](implicit request: Request[A]) = {
			for {
				token <- Future(request.getQueryString("sso").get)
				user <- DB.run {
					for {
						session <- PhpBBSessions.filter(_.id === token).result.head
						user <- Users.filter(u => u.id === session.user && u.group.inSet(AuthService.allowed_groups)).result.head
					} yield user
				}
				ip = Some(request.remoteAddress)
				ua = request.headers.get("User-Agent")
				session_token <- AuthService.createSession(user.id, ip, ua)
			} yield (user, session_token, true)
		}

		// Wrap the request inside a UserRequest
		def transform[A](implicit request: Request[A]) = {
			for {
				(user, token, set_token) <- cookieUser recoverWith { case _ => ssoUser }
			} yield new UserRequest(user, token, set_token, request)
		}

		// Action wrapper
		override def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[Result]) = {
			val result = for {
				req <- transform(request) recover { case _ => throw AuthFailed }
				res <- block(req)
			} yield {
				if (req.set_cookie) res.withCookies(Cookie("gt_session", req.token, maxAge = Some(60 * 60 * 24 * 7)))
				else res
			}

			result recover {
				case Deny => Redirect("/wt/")
				case AuthFailed => Ok(views.html.wt.unauthenticated.render())
			}
		}
	}

	def main = UserAction { request => Ok(views.html.wt.main.render(request.user)) }

	def catchall(path: String) = Action {
		Redirect("/wt/")
	}
}
