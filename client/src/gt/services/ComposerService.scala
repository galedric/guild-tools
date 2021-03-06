package gt.services

import boopickle.DefaultBasic._
import gt.services.base.{Cache, Delegate, Service}
import models.composer.ComposerDocument
import scala.concurrent.{Future, Promise}
import utils.EventSource

object ComposerService extends Service with Delegate {
	private val channel = registerChannel("composer")

	private var promise: Promise[Unit] = Promise()
	def ready: Future[Unit] = promise.future

	val documentDeleted = new EventSource[Int]

	object documents extends Cache((doc: ComposerDocument) => doc.id)

	def createDocument(title: String, style: String): Unit = channel.send("create-document", (title, style))
	def renameDocument(id: Int, title: String): Unit = channel.send("rename-document", (id, title))
	def deleteDocument(id: Int): Unit = channel.send("delete-document", id)

	message("document-updated")(documents.update _)
	message("document-deleted") { key: Int =>
		documents.removeKey(key)
		documentDeleted.emit(key)
	}

	override protected def enable(): Unit = {
		channel.request("load-documents") { (docs: Seq[ComposerDocument]) =>
			docs.foreach(documents.update)
			promise.success(())
		}
	}

	override protected def disable(): Unit = {
		documents.clear()
		promise = Promise()
	}
}
