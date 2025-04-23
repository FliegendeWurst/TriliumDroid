package eu.fliegendewurst.triliumdroid.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
import android.util.Log
import eu.fliegendewurst.triliumdroid.data.AttachmentId
import eu.fliegendewurst.triliumdroid.data.Blob
import eu.fliegendewurst.triliumdroid.data.BlobId
import eu.fliegendewurst.triliumdroid.data.NoteId
import eu.fliegendewurst.triliumdroid.data.RevisionId
import eu.fliegendewurst.triliumdroid.database.Cache.dateModified
import eu.fliegendewurst.triliumdroid.database.Cache.utcDateModified
import eu.fliegendewurst.triliumdroid.service.ProtectedSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object Blobs {
	private const val TAG = "Blobs"

	private val blobCache: MutableMap<String, Blob> = WeakHashMap()

	/**
	 * Calculate content hash of Blob.
	 *
	 * Trilium's reference implementation:
	 * [utils.hashedBlobId](https://github.com/TriliumNext/Notes/blob/v0.93.0/src/services/utils.ts#L44-L57),
	 * [AbstractBeccaEntity#getUnencryptedContentForHashCalculation](https://github.com/TriliumNext/Notes/blob/v0.93.0/src/becca/entities/abstract_becca_entity.ts#L204-L212).
	 */
	@OptIn(ExperimentalEncodingApi::class)
	fun calcHash(content: ByteArray?, encrypted: Boolean): BlobId {
		val md = MessageDigest.getInstance("SHA512")
		if (encrypted) {
			md.update("t$[nvQg7q)&_ENCRYPTED_?M:Bf&j3jr_".encodeToByteArray())
		}
		md.update(content ?: byteArrayOf())
		return BlobId(
			Base64.encode(md.digest())
				.replace('+', 'X')
				.replace('/', 'Y')
				.substring(0 until 20)
		)
	}

	suspend fun new(content: ByteArray?, contentForHash: ByteArray? = null): Blob =
		withContext(Dispatchers.IO) {
			val blobId = calcHash(contentForHash ?: content, contentForHash != null)
			Log.d(TAG, "creating new blob ${blobId.rawId()}")
			val existingBlob = load(blobId)
			if (existingBlob != null) {
				return@withContext existingBlob
			}
			val dateModified = dateModified()
			val utcDateModified = utcDateModified()

			val cv = ContentValues()
			cv.put("blobId", blobId.id)
			cv.put("content", content ?: ByteArray(0))
			cv.put("dateModified", dateModified)
			cv.put("utcDateModified", utcDateModified)

			if (DB.insertWithConflict(
					"blobs",
					CONFLICT_FAIL,
					Pair("blobId", blobId),
					Pair("content", content ?: ByteArray(0)),
					Pair("dateModified", dateModified),
					Pair("utcDateModified", utcDateModified)
				) == -1L
			) {
				Log.w(TAG, "failed to insert new blobId = ${blobId.id}")
				return@withContext new(content)
			}
			val blob = Blob(blobId, content ?: ByteArray(0), dateModified, utcDateModified)
			registerEntityChangeBlob(blob)
			return@withContext blob
		}

	suspend fun load(id: BlobId): Blob? = withContext(Dispatchers.IO) {
		val it = blobCache[id.id]
		if (it != null) {
			return@withContext it
		}
		DB.internalGetDatabase()!!.query(
			"blobs",
			arrayOf(
				"content", "dateModified", "utcDateModified"
			),
			"blobId = ?",
			arrayOf(id.id),
			null, null, null
		).use {
			if (it.moveToNext()) {
				val content = it.getBlob(0)
				val dateModified = it.getString(1)
				val utcDateModified = it.getString(2)
				val blob = Blob(id, content, dateModified, utcDateModified)
				blobCache[id.id] = blob
				return@withContext blob
			}
		}
		Log.w(TAG, "cannot find blobId = ${id.id}")
		return@withContext null
	}

	fun loadInternal(blob: Blob) {
		blobCache[blob.id.id] = blob
	}

	/**
	 * Delete a blob, if it is no longer used.
	 *
	 * Trilium's reference implementation: [AbstractBeccaEntity#deleteBlobIfNotUsed](https://github.com/TriliumNext/Notes/blob/v0.93.0/src/becca/entities/abstract_becca_entity.ts#L185-L202).
	 *
	 * @return whether the blob was deleted
	 */
	suspend fun delete(id: BlobId) = withContext(Dispatchers.IO) {
		if (notesWithBlob(id).isNotEmpty() || attachmentsWithBlob(id).isNotEmpty() ||
			revisionsWithBlob(id).isNotEmpty()
		) {
			return@withContext false
		}
		DB.delete("blobs", "blobId", arrayOf(id.rawId()))
		DB.internalGetDatabase()!!
			.delete("entity_changes", "entityName = 'blobs' AND entityId = ?", arrayOf(id.id))
		blobCache.remove(id.id)
		return@withContext true
	}

	suspend fun notesWithBlob(id: BlobId): List<NoteId> = withContext(Dispatchers.IO) {
		val l = mutableListOf<NoteId>()
		DB.internalGetDatabase()!!
			.query("notes", arrayOf("noteId"), "blobId = ?", arrayOf(id.id), null, null, null)
			.use {
				while (it.moveToNext()) {
					l.add(NoteId(it.getString(0)))
				}
			}
		return@withContext l
	}

	suspend fun attachmentsWithBlob(id: BlobId): List<AttachmentId> =
		withContext(Dispatchers.IO) {
			val l = mutableListOf<AttachmentId>()
			DB.internalGetDatabase()!!.query(
				"attachments",
				arrayOf("attachmentId"),
				"blobId = ?",
				arrayOf(id.id),
				null,
				null,
				null
			)
				.use {
					while (it.moveToNext()) {
						l.add(AttachmentId(it.getString(0)))
					}
				}
			return@withContext l
		}

	suspend fun revisionsWithBlob(id: BlobId): List<RevisionId> = withContext(Dispatchers.IO) {
		val l = mutableListOf<RevisionId>()
		DB.internalGetDatabase()!!.query(
			"revisions", arrayOf("revisionId"), "blobId = ?", arrayOf(id.id), null, null, null
		)
			.use {
				while (it.moveToNext()) {
					l.add(RevisionId(it.getString(0)))
				}
			}
		return@withContext l
	}

	suspend fun fixupBrokenBlobIDs() {
		// fix corrupted blob IDs
		val error = ProtectedSession.enter()
		if (error != null) {
			Log.e(TAG, "failed to enter protected session for database migration: $error")
		}
		val affected = mutableListOf<BlobId>()
		DB.internalGetDatabase()!!.query(
			"entity_changes",
			arrayOf("entityId"),
			"entityName = 'blobs'",
			arrayOf(),
			null,
			null,
			null
		).use { cursor ->
			while (cursor.moveToNext()) {
				affected.add(BlobId(cursor.getString(0)))
			}
		}
		for (blobId in affected) {
			val blob = load(blobId) ?: continue
			// check if blob is still used
			if (delete(blobId)) {
				// very special case: we should propagate the deletion to the sync server
				registerEntityChangeBlob(blob, true)
				continue
			}
			val notes = notesWithBlob(blobId).mapNotNull { Notes.getNote(it) }
			val attachments =
				attachmentsWithBlob(blobId).mapNotNull { Attachments.load(it) }
			val revisions = revisionsWithBlob(blobId).mapNotNull { NoteRevisions.load(it) }
			val encrypted =
				notes.any { it.isProtected } || attachments.any { it.protected } || revisions.any { it.isProtected }
			// check if blob ID is good
			// (depends on whether it is encrypted)
			if (encrypted) {
				val decrypted = blob.decrypt()
				if (decrypted == null) {
					Log.e(TAG, "failed to decrypt blob for checking: $blobId")
					continue
				}
				val correctId = calcHash(decrypted, true)
				if (blobId == correctId) {
					continue
				}
				Log.w(TAG, "found encrypted blob $blobId with incorrect blobId")
				val correctBlob = new(blob.content, decrypted)
				notes.forEach {
					Notes.setBlobId(it.id, correctBlob.id)
				}
				attachments.forEach {
					Attachments.setBlobId(it.id, correctBlob.id)
				}
				revisions.forEach {
					NoteRevisions.setBlobId(it.revisionId, correctBlob.id)
				}
				val nowDeleted = delete(blob.id)
				if (!nowDeleted) {
					Log.e(TAG, "failed to delete corrupted blob $blobId")
				} else {
					registerEntityChangeBlob(blob, true)
				}
			} else {
				val correctId = calcHash(blob.content, false)
				if (blobId == correctId) {
					continue
				}
				val correctBlob = new(blob.content, blob.content)
				notes.forEach {
					Notes.setBlobId(it.id, correctBlob.id)
				}
				attachments.forEach {
					Attachments.setBlobId(it.id, correctBlob.id)
				}
				revisions.forEach {
					NoteRevisions.setBlobId(it.revisionId, correctBlob.id)
				}
				val nowDeleted = delete(blob.id)
				if (!nowDeleted) {
					Log.e(TAG, "failed to delete corrupted blob $blobId")
				} else {
					registerEntityChangeBlob(blob, true)
				}
			}
		}
		ProtectedSession.leave()
	}
}

private suspend fun registerEntityChangeBlob(b: Blob, deleted: Boolean = false) {
	// hash ["blobId", "content"]
	// source: https://github.com/TriliumNext/Notes/blob/develop/src/becca/entities/bblob.ts
	Cache.registerEntityChange(
		"blobs",
		b.id.id,
		listOf(
			b.id.id.encodeToByteArray(),
			b.content
		),
		deleted
	)
}
