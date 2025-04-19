package eu.fliegendewurst.triliumdroid.database

import android.content.ContentValues
import android.util.Log
import androidx.core.database.getStringOrNull
import eu.fliegendewurst.triliumdroid.data.Attachment
import eu.fliegendewurst.triliumdroid.data.AttachmentId
import eu.fliegendewurst.triliumdroid.data.BlobId
import eu.fliegendewurst.triliumdroid.data.NoteId
import eu.fliegendewurst.triliumdroid.data.asAttachmentRole
import eu.fliegendewurst.triliumdroid.data.asString
import eu.fliegendewurst.triliumdroid.database.Cache.db
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Attachments {
	private const val TAG = "Attachments"

	suspend fun load(id: AttachmentId): Attachment? = withContext(Dispatchers.IO) {
		db!!.query(
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
		val cv = ContentValues()
		cv.put("blobId", blobId.id)
		db!!.update("attachments", cv, "attachmentId = ?", arrayOf(id.id))
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
