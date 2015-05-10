package gtp3

import scodec.{Codec, _}
import scodec.bits.{BitVector, ByteVector}
import scodec.codecs._

private object g {
	val str = variableSizeBytes(uint16, utf8)
	val bool = scodec.codecs.bool(8)
	val buf = variableSizeBytes(uint16, bytes)
}

sealed trait Frame

sealed trait SequencedFrame {
	val seq: Int
}

sealed trait ChannelFrame extends SequencedFrame {
	val channel: Int
}

sealed trait PayloadFrame {
	val flags: Int
	val payload: ByteVector
}

case class BadFrame() extends Frame
object BadFrame {
	implicit val discriminator = Discriminator[Frame, BadFrame, Int](0xFF)
	implicit val codec = fail(Err.General("", Nil)).as[BadFrame]
}

case class HelloFrame(magic: Int, version: String) extends Frame
object HelloFrame {
	implicit val discriminator = Discriminator[Frame, HelloFrame, Int](FrameType.HELLO)
	implicit val codec = (int32 :: g.str).as[HelloFrame]
}

case class HandshakeFrame(magic: Int, version: String, sockid: Long) extends Frame
object HandshakeFrame {
	implicit val discriminator = Discriminator[Frame, HandshakeFrame, Int](FrameType.HANDSHAKE)
	implicit val codec = (int32 :: g.str :: int64).as[HandshakeFrame]
}

case class ResumeFrame(sockid: Long, last_seq: Int) extends Frame
object ResumeFrame {
	implicit val discriminator = Discriminator[Frame, ResumeFrame, Int](FrameType.RESUME)
	implicit val codec = (int64 :: uint16).as[ResumeFrame]
}

case class SyncFrame(last_seq: Int) extends Frame
object SyncFrame {
	implicit val discriminator = Discriminator[Frame, SyncFrame, Int](FrameType.SYNC)
	implicit val codec = uint16.as[SyncFrame]
}

case class AckFrame(last_seq: Int) extends Frame
object AckFrame {
	implicit val discriminator = Discriminator[Frame, AckFrame, Int](FrameType.ACK)
	implicit val codec = uint16.as[AckFrame]
}

case class ByeFrame(code: Int, message: String) extends Frame
object ByeFrame {
	implicit val discriminator = Discriminator[Frame, ByeFrame, Int](FrameType.BYE)
	implicit val codec = (uint16 :: variableSizeBytes(uint16, utf8)).as[ByeFrame]
}

case class IgnoreFrame(padding: ByteVector) extends Frame
object IgnoreFrame {
	implicit val discriminator = Discriminator[Frame, IgnoreFrame, Int](FrameType.BYE)
	implicit val codec = bytes.as[IgnoreFrame]
}

case class CommandFrame(op: Int) extends Frame
object CommandFrame {
	implicit val discriminator = Discriminator[Frame, CommandFrame, Int](FrameType.COMMAND)
	implicit val codec = uint16.as[CommandFrame]
}

case class OpenFrame(seq: Int, sender_channel: Int,
		channel_type: String, token: String, parent_channel: Int) extends Frame with SequencedFrame
object OpenFrame {
	implicit val discriminator = Discriminator[Frame, OpenFrame, Int](FrameType.OPEN)
	implicit val codec = (uint16 :: uint16 :: g.str :: g.str :: uint16).as[OpenFrame]
}

case class OpenSuccessFrame(seq: Int, recipient_channel: Int, sender_channel: Int) extends Frame with SequencedFrame
object OpenSuccessFrame {
	implicit val discriminator = Discriminator[Frame, OpenSuccessFrame, Int](FrameType.OPEN_SUCCESS)
	implicit val codec = (uint16 :: uint16 :: uint16).as[OpenSuccessFrame]
}

case class OpenFailureFrame(seq: Int, recipient_channel: Int, code: Int, message: String) extends Frame with SequencedFrame
object OpenFailureFrame {
	implicit val discriminator = Discriminator[Frame, OpenFailureFrame, Int](FrameType.OPEN_FAILURE)
	implicit val codec = (uint16 :: uint16 :: uint16 :: g.str).as[OpenFailureFrame]
}

case class DestroyFrame(sender_channel: Int) extends Frame
object DestroyFrame {
	implicit val discriminator = Discriminator[Frame, DestroyFrame, Int](FrameType.DESTROY)
	implicit val codec = uint16.as[DestroyFrame]
}

case class MessageFrame(seq: Int, channel: Int,
		message: String, flags: Int, payload: ByteVector) extends Frame with ChannelFrame with PayloadFrame
object MessageFrame {
	implicit val discriminator = Discriminator[Frame, MessageFrame, Int](FrameType.MESSAGE)
	implicit val codec = (uint16 :: uint16 :: g.str :: uint16 :: g.buf).as[MessageFrame]
}

case class RequestFrame(seq: Int, channel: Int,
		request: String, id: Int, flags: Int, payload: ByteVector) extends Frame with ChannelFrame with PayloadFrame
object RequestFrame {
	implicit val discriminator = Discriminator[Frame, RequestFrame, Int](FrameType.REQUEST)
	implicit val codec = (uint16 :: uint16 :: g.str :: uint16 :: uint16 :: g.buf).as[RequestFrame]
}

case class SuccessFrame(seq: Int, channel: Int,
		request: Int, flags: Int, payload: ByteVector) extends Frame with ChannelFrame with PayloadFrame
object SuccessFrame {
	implicit val discriminator = Discriminator[Frame, SuccessFrame, Int](FrameType.SUCCESS)
	implicit val codec = (uint16 :: uint16 :: uint16 :: uint16 :: g.buf).as[SuccessFrame]
}

case class FailureFrame(seq: Int, channel: Int,
		request: Int, code: Int, message: String) extends Frame with ChannelFrame
object FailureFrame {
	implicit val discriminator = Discriminator[Frame, FailureFrame, Int](FrameType.FAILURE)
	implicit val codec = (uint16 :: uint16 :: uint16 :: uint16 :: g.str).as[FailureFrame]
}

case class CloseFrame(seq: Int, channel: Int, code: Int, message: String) extends Frame with ChannelFrame
object CloseFrame {
	implicit val discriminator = Discriminator[Frame, CloseFrame, Int](FrameType.CLOSE)
	implicit val codec = (uint16 :: uint16 :: uint16 :: g.str).as[CloseFrame]
}

object Frame {
	implicit val discriminated = Discriminated[Frame, Int](uint8)
	val codec = Codec.coproduct[Frame].auto.asInstanceOf[Codec[Frame]]
	def decode(buffer: Array[Byte]) = codec.decode(BitVector(buffer)).fold(_ => BadFrame(), res => res.value)
	def encode(frame: Frame) = codec.encode(frame).require
}
