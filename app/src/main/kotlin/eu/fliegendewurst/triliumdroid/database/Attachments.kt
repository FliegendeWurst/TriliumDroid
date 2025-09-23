package eu.fliegendewurst.triliumdroid.database

import android.util.Log
import androidx.core.database.getStringOrNull
import eu.fliegendewurst.triliumdroid.data.Attachment
import eu.fliegendewurst.triliumdroid.data.AttachmentId
import eu.fliegendewurst.triliumdroid.data.AttachmentRole
import eu.fliegendewurst.triliumdroid.data.BlobId
import eu.fliegendewurst.triliumdroid.data.NoteId
import eu.fliegendewurst.triliumdroid.data.asAttachmentRole
import eu.fliegendewurst.triliumdroid.data.asString
import eu.fliegendewurst.triliumdroid.service.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Attachments {
	private const val TAG = "Attachments"

	suspend fun create(
		note: NoteId,
		role: AttachmentRole,
		title: String,
		mime: String,
		content: BlobId,
		position: Int
	): AttachmentId = withContext(
		Dispatchers.IO
	) {
		var newId = AttachmentId(DB.newId("attachments", "attachmentId") { Util.newNoteId() })
		DB.insert(
			"attachments",
			Pair("attachmentId", newId.rawId()),
			Pair("ownerId", note.rawId()),
			Pair("role", role.asString()),
			Pair("mime", mime),
			Pair("title", title),
			Pair("isProtected", 0),
			Pair("position", position),
			Pair("blobId", content.rawId()),
			Pair("dateModified", Cache.dateModified()),
			Pair("utcDateModified", Cache.utcDateModified()),
			Pair("utcDateScheduledForErasureSince", null),
			Pair("isDeleted", 0),
			Pair("deleteId", null)
		)
		val attachment = load(newId)!!
		registerEntityChangeAttachment(attachment)
		return@withContext newId
	}

	suspend fun find(noteId: NoteId, attachmentRole: AttachmentRole): Attachment? = withContext(
		Dispatchers.IO
	) {
		DB.internalGetDatabase()!!.query(
			"attachments",
			arrayOf("attachmentId"),
			"ownerId = ? AND role = ?",
			arrayOf(noteId.rawId(), attachmentRole.asString()),
			null, null, null
		).use {
			if (it.moveToNext()) {
				val id = AttachmentId(it.getString(0))
				return@withContext load(id)
			}
		}
		return@withContext null
	}

	suspend fun load(id: AttachmentId): Attachment? = withContext(Dispatchers.IO) {
		DB.internalGetDatabase()!!.query(
			"attachments",
			arrayOf(
				"ownerId",
				"role",
				"mime",
				"title",
				"isProtected",
				"position",
				"blobId",
				"dateModified",
				"utcDateModified",
				"utcDateScheduledForErasureSince",
				"isDeleted",
				"deleteId"
			),
			"attachmentId = ?",
			arrayOf(id.id),
			null,
			null,
			null
		)
			.use {
				if (it.moveToNext()) {
					val ownerId = it.getString(0)
					val role = it.getString(1).asAttachmentRole()
					if (role == null) {
						Log.e(TAG, "attachment $id has unknown role ${it.getString(1)}")
						return@withContext null
					}
					val mime = it.getString(2)
					val title = it.getString(3)
					val isProtected = it.getInt(4).intValueToBool()
					val position = it.getInt(5)
					val blobId = it.getStringOrNull(6)
					val dateModified = it.getString(7)
					val utcDateModified = it.getString(8)
					val utcDateScheduledForErasureSince = it.getStringOrNull(9)
					val isDeleted = it.getInt(10).intValueToBool()
					val deleteId = it.getStringOrNull(11)
					return@withContext Attachment(
						id,
						NoteId(ownerId),
						role,
						mime,
						title,
						isProtected,
						position,
						if (blobId != null) {
							BlobId(blobId)
						} else {
							null
						},
						dateModified,
						utcDateModified,
						utcDateScheduledForErasureSince,
						isDeleted,
						deleteId
					)
				}
			}
		return@withContext null
	}

	suspend fun setBlobId(id: AttachmentId, blobId: BlobId) = withContext(Dispatchers.IO) {
		DB.update(id, Pair("blobId", blobId))
		registerEntityChangeAttachment(load(id)!!)
	}
}

private suspend fun registerEntityChangeAttachment(attachment: Attachment) {
	// https://github.com/TriliumNext/Notes/blob/v0.93.0/src/becca/entities/battachment.ts#L41
	// hash ["attachmentId", "ownerId", "role", "mime", "title", "blobId", "utcDateScheduledForErasureSince"]
	Cache.registerEntityChange(
		"attachments",
		attachment.id.id,
		arrayOf(
			attachment.id.id,
			attachment.ownerId.id,
			attachment.role.asString(),
			attachment.mime,
			attachment.title,
			attachment.blobId?.id,
			attachment.utcDateScheduledForErasureSince
		)
	)
}
