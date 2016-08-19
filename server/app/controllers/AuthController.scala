package controllers

import actors.AuthService
import gt.GuildTools
import java.util.concurrent.ExecutionException
import model.User
import models._
import models.authentication.Sessions
import models.mysql._
import play.api.mvc._
import reactive.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try
import utils.Implicits._
import utils.{Cache, TokenBucket}

class AuthController extends Controller {
	/** Base path of the Auth interface */
	private val base = if (GuildTools.prod) "" else "/auth"

	/** The path of the main auth page */
	private def url(path: String) = base + path

	/** Token buckets for rate-limiting the /sso page */
	private val sso_buckets = Cache(1.hour) { (source: String) => new TokenBucket(15, 10000) }

	/**
	  * Guesses the correct service URL for redirection after authentication.
	  *
	  * @param service The service code
	  */
	private def serviceURL(service: String, default: String = url("/")) = {
		service match {
			case "www" => "https://www.fromscratch.gg/sso.php"

			case "gt" if GuildTools.prod => "https://gt.fromscratch.gg/sso"
			case "gt" if GuildTools.dev => "/sso"

			case _ => default
		}
	}

	/**
	  * Constructs the session cookie.
	  *
	  * @param session The session token
	  */
	private def sessionCookie(session: String) = Cookie(
		name = "FSID",
		value = session,
		maxAge = Some(60 * 60 * 24 * 5),
		httpOnly = true,
		secure = true
	)

	/** A request with user information */
	class AuthRequest[A](val optUser: Option[User], request: Request[A])
			extends WrappedRequest[A](request) {
		val user = optUser.orNull
		val authenticated = optUser.isDefined

		val sessid = request.cookies.get("FSID").map(_.value).getOrElse("")

		def url(path: String) = AuthController.this.url(path)
	}

	/** Authenticated action */
	object AuthAction extends ActionBuilder[AuthRequest] {
		def transform[A](request: Request[A]) = {
			val ip = Some(request.remoteAddress)
			val ua = request.headers.get("User-Agent")
			for {
				user <- request.cookies.get("FSID").map(_.value) match {
					case Some(sessid) => AuthService.auth(sessid, ip, ua).map(Some(_)).recover { case _ => None }
					case _ => Future.successful(None)
				}
			} yield {
				new AuthRequest(user, request)
			}
		}

		override def invokeBlock[A](request: Request[A], block: (AuthRequest[A]) => Future[Result]) = {
			transform(request).flatMap { implicit req =>
				if (req.secure) block(req)
				else Redirect(url("/nonsecure"))
			}
		}
	}

	/** Only allow authenticated users to access the action */
	val Authenticated = AuthAction andThen new ActionFilter[AuthRequest] {
		def filter[A](request: AuthRequest[A]) = Future.successful {
			if (!request.authenticated) {
				Some(Redirect(url("/")))
			} else {
				None
			}
		}
	}

	/**
	  * Main login form.
	  */
	def main = Action.async { req =>
		if (req.secure) {
			val valid = req.cookies.get("FSID") match {
				case Some(cookie) => AuthService.sessionActive(cookie.value)
				case None => Future.successful(false)
			}

			valid.map {
				case true => Redirect(url("/account"))
				case false => Ok(views.html.auth.main.render(req.flash.get("error").orElse(req.getQueryString("error"))))
			}
		} else {
			Redirect(url("/nonsecure"))
		}
	}

	/**
	  * Perform authentication.
	  * This action is indirectly rate-limited by the call to AuthService.login
	  */
	def auth = Action.async { req =>
		utils.atLeast(1.seconds) {
			Try {
				val post = req.body.asFormUrlEncoded.get.map { case (k, v) => (k, v.headOption.getOrElse("")) }
				val user = post("user").trim
				val pass = post("pass").trim

				AuthService.login(user, pass, Some(req.remoteAddress), req.headers.get("User-Agent")).map { session =>
					Redirect(serviceURL(req.session.get("service").getOrElse("")), Map(
						"session" -> Seq(session),
						"token" -> Seq(req.session.get("token").getOrElse(""))
					)).withCookies(sessionCookie(session)).withNewSession
				}.recover {
					case e: ExecutionException => Redirect(url("/")).flashing("error" -> e.getCause.getMessage)
					case e => Redirect(url("/")).flashing("error" -> e.getMessage)
				}
			}.getOrElse {
				Redirect(url("/")).flashing("error" -> "An unknown error occured")
			}
		}
	}

	/**
	  * Single sign-on page.
	  * Will redirect the user back to the service if the session is valid or
	  * display the login page if it is not.
	  */
	def sso = Action.async { req =>
		val service = req.getQueryString("service").getOrElse("")
		val token = req.getQueryString("token").getOrElse("")
		val session = req.cookies.get("FSID")
		val ignore = req.getQueryString("noauth").isDefined

		// Current session is valid, redirect to service
		def success_redirect =
			Redirect(serviceURL(service), Map(
				"session" -> Seq(session.get.value),
				"token" -> Seq(token)
			)).withCookies(sessionCookie(session.get.value))

		// Current session is invalid, but the service does not want authentication
		def ignore_redirect =
			Redirect(serviceURL(service, url("/")), Map(
				"session" -> Seq(""),
				"token" -> Seq(token)
			))

		// Current session is invalid and service is requesting authentication
		def auth_redirect =
			Redirect(url("/")).withSession {
				req.session + ("service" -> service) + ("token" -> token)
			}

		if (sso_buckets(req.remoteAddress).take()) {
			val valid = session match {
				case Some(cookie) => AuthService.sessionActive(cookie.value)
				case None => Future.successful(false)
			}

			valid.map {
				case true => success_redirect
				case false if ignore => ignore_redirect
				case false => auth_redirect
			}
		} else if (req.getQueryString("noauth").isDefined) {
			ignore_redirect
		} else {
			Redirect(url("/throttled"))
		}
	}

	/**
	  * Logouts
	  */
	def logout = Action.async { req =>
		val service = req.getQueryString("service").getOrElse("")
		val token = req.getQueryString("token").getOrElse("")
		req.cookies.get("FSID").map { cookie =>
			AuthService.logout(cookie.value)
		}.getOrElse(Future.successful(())).map { _ =>
			Redirect(serviceURL(service, url("/")), Map(
				"logout" -> Seq("yes"),
				"token" -> Seq(token)
			)).withCookies(Cookie("FSID", ""))
		}
	}

	/**
	  * Main account page
	  */
	def account = Authenticated.async { implicit req =>
		for {
			opt_profile <- Profiles.findById(req.user.id).headOption
		} yield {
			val profile = opt_profile.map(_.withPlaceholders).getOrElse(Profiles.empty)
			Ok(views.html.auth.account.render(profile, req))
		}
	}

	/**
	  * Lists active sessions
	  */
	def sessions = Authenticated.async { implicit req =>
		val close = req.getQueryString("close") match {
			case Some("all") =>
				Sessions.findByUser(req.user.id).run.flatMap { sessions =>
					Future.sequence {
						sessions.filter(_.token != req.sessid).map { session =>
							AuthService.logout(session.token)
						}
					}
				}

			case Some(session) =>
				Sessions.filter(_.token === session).map(_.user).head.flatMap {
					case id if id == req.user.id => AuthService.logout(session)
					case _ => Future.successful(())
				}

			case None =>
				Future.successful(())
		}

		for {
			_ <- close
			sessions <- Sessions.findByUser(req.user.id).sortBy(_.last_access.desc).run
		} yield {
			Ok(views.html.auth.sessions.render(sessions, req))
		}
	}

	/**
	  * Non-secure connection
	  */
	def nonsecure = Action { Ok(views.html.auth.nonsecure.render()) }

	/**
	  * Throttled
	  */
	def throttled = Action { Ok(views.html.auth.throttled.render()) }
}