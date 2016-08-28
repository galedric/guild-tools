package gt.services

import boopickle.DefaultBasic._
import gt.services.base.{Cache, Delegate, Service}
import models.composer.ComposerDocument
import scala.concurrent.{Future, Promise}

object ComposerService extends Service with Delegate {
	private val channel = registerChannel("composer")

	private var promise: Promise[Unit] = Promise()
	def ready: Future[Unit] = promise.future

	object documents extends Cache((doc: ComposerDocument) => doc.id) {
	}

	def createDocument(title: String, style: String): Unit = channel.send("create-document", (title, style))

	message("document-updated")(documents.update _)
	message("document-removed")(documents.removeKey _)

	override protected def enable(): Unit = {
		println("enable")
		channel.request("load-documents") { (docs: Seq[ComposerDocument]) =>
			docs.foreach(documents.update)
			promise.success(())
		}
	}

	override protected def disable(): Unit = {
		println("disable")
		documents.clear()
		promise = Promise()
	}
}