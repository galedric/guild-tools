package channels

import scala.concurrent.Future
import scala.concurrent.duration._
import play.api.libs.json.{JsNull, JsValue, Json}
import actors.Actors._
import gtp3._
import reactive._
import models._

object Master extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(new Master)
}

class Master extends ChannelHandler {
	val handlers = Map[String, Handler]()

	def init() = {}
	def login(payload: Payload): Future[Payload] = ???
}