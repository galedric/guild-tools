package gtp3

import akka.actor.{ActorRef, Props}
import gtp3.Socket.Opener
import models.User

abstract class ChannelRequest(val socket: ActorRef, val channel_type: String, val token: String, val user: User, val opener: Opener) {
	protected var _channel: Option[Channel] = None
	def channel = _channel

	protected var _replied = false
	def replied = _replied

	def accept(handler: Props): Unit
	def reject(code: Int, message: String): Unit
}
