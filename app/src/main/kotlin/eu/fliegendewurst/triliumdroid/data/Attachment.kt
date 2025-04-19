package eu.fliegendewurst.triliumdroid.data

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

data class AttachmentId(val id: String)

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
