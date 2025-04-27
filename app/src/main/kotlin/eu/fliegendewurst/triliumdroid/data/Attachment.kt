package eu.fliegendewurst.triliumdroid.data

import eu.fliegendewurst.triliumdroid.database.IdLike

class Attachment(
	var id: AttachmentId,
	val ownerId: NoteId,
	val role: AttachmentRole,
	val mime: String,
	val title: String,
	val protected: Boolean,
	val position: Int,
	val blobId: BlobId?,
	val dateModified: String,
	val utcDateModified: String,
	val utcDateScheduledForErasureSince: String?,
	val deleted: Boolean,
	val deleteId: String?
) {
}

data class AttachmentId(val id: String) : IdLike {
	override fun rawId() = id
	override fun columnName() = "attachmentId"
	override fun tableName() = "attachments"
	override fun toString() = id
}

enum class AttachmentRole {
	File,
	Image
}

fun String.asAttachmentRole(): AttachmentRole? =
	when (this) {
		"file" -> AttachmentRole.File
		"image" -> AttachmentRole.Image
		else -> null
	}

fun AttachmentRole.asString(): String =
	when (this) {
		AttachmentRole.File -> "file"
		AttachmentRole.Image -> "image"
	}
