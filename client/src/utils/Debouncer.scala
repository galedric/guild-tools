package utils

import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}

case class Debouncer[T](delay: FiniteDuration)(block: => T) {
	private[this] var deadline: Deadline = null
	private[this] var scheduled: SetTimeoutHandle = null

	def trigger(): Unit = {
		deadline = delay.fromNow
		if (scheduled == null) schedule()
	}

	def now(flush: Boolean = false): Unit = if (!flush || scheduled != null) {
		cancel()
		block
	}

	private def cancel(): Unit = if (scheduled != null) {
		clearTimeout(scheduled)
		scheduled = null
	}

	private def schedule(): Unit = {
		cancel()
		scheduled = setTimeout(deadline.timeLeft) {
			if (deadline != null) {
				scheduled = null
				if (deadline.isOverdue) {
					deadline = null
					block
				} else {
					schedule()
				}
			}
		}
	}
}
