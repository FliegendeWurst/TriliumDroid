package eu.fliegendewurst.triliumdroid.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
import android.util.Log
import eu.fliegendewurst.triliumdroid.data.Blob
import eu.fliegendewurst.triliumdroid.database.Cache.dateModified
import eu.fliegendewurst.triliumdroid.database.Cache.db
import eu.fliegendewurst.triliumdroid.database.Cache.utcDateModified
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
	 * Trilium's [reference implementation](https://github.com/TriliumNext/Notes/blob/v0.93.0/src/services/utils.ts#L44-L57).
	 */
	@OptIn(ExperimentalEncodingApi::class)
	fun calcHash(content: ByteArray?): String {
		val md = MessageDigest.getInstance("SHA512")
		md.update(content ?: byteArrayOf())
		return Base64.encode(md.digest())
			.replace('+', 'X')
			.replace('/', 'Y')
			.substring(0..19)
	}

	suspend fun new(content: ByteArray?, contentForHash: ByteArray? = null): Blob =
		withContext(Dispatchers.IO) {
			val blobId = calcHash(contentForHash ?: content)
			val existingBlob = load(blobId)
			if (existingBlob != null) {
				return@withContext existingBlob
			}
			val dateModified = dateModified()
			val utcDateModified = utcDateModified()

			val cv = ContentValues()
			cv.put("blobId", blobId)
			cv.put("content", content ?: ByteArray(0))
			cv.put("dateModified", dateModified)
			cv.put("utcDateModified", utcDateModified)

			if (db!!.insertWithOnConflict("blobs", null, cv, CONFLICT_FAIL) == -1L) {
				Log.w(TAG, "failed to insert new blobId = $blobId")
				return@withContext new(content)
			}
			val blob = Blob(blobId, content ?: ByteArray(0), dateModified, utcDateModified)
			registerEntityChangeBlob(blob)
			return@withContext blob
		}

	suspend fun load(blobId: String): Blob? = withContext(Dispatchers.IO) {
		val it = blobCache[blobId]
		if (it != null) {
			return@withContext it
		}
		db!!.query(
			"blobs",
			arrayOf(
				"content", "dateModified", "utcDateModified"
			),
			"blobId = ?",
			arrayOf(blobId),
			null, null, null
		).use {
			if (it.moveToNext()) {
				val content = it.getBlob(0)
				val dateModified = it.getString(1)
				val utcDateModified = it.getString(2)
				val blob = Blob(blobId, content, dateModified, utcDateModified)
				blobCache[blobId] = blob
				return@withContext blob
			}
		}
		Log.w(TAG, "cannot find blobId = $blobId")
		return@withContext null
	}

	fun loadInternal(blob: Blob) {
		blobCache[blob.blobId] = blob
	}

	/**
	 * Delete a blob, if it is no longer used.
	 *
	 * Trilium's reference implementation: [AbstractBeccaEntity#deleteBlobIfNotUsed](https://github.com/TriliumNext/Notes/blob/v0.93.0/src/becca/entities/abstract_becca_entity.ts#L185-L202).
	 *
	 * @return whether the blob was deleted
	 */
	suspend fun delete(blobId: String) = withContext(Dispatchers.IO) {
		db!!.query("notes", arrayOf("noteId"), "blobId = ?", arrayOf(blobId), null, null, null)
			.use {
				if (it.moveToNext()) {
					return@withContext false
				}
			}
		db!!.query(
			"attachments",
			arrayOf("attachmentId"),
			"blobId = ?",
			arrayOf(blobId),
			null,
			null,
			null
		).use {
			if (it.moveToNext()) {
				return@withContext false
			}
		}
		db!!.query(
			"revisions",
			arrayOf("revisionId"),
			"blobId = ?",
			arrayOf(blobId),
			null,
			null,
			null
		).use {
			if (it.moveToNext()) {
				return@withContext false
			}
		}
		db!!.delete("blobs", "blobId = ?", arrayOf(blobId))
		db!!.delete("entity_changes", "entityName = 'blobs' AND entityId = ?", arrayOf(blobId))
		blobCache.remove(blobId)
		return@withContext true
	}
}

private suspend fun registerEntityChangeBlob(b: Blob, deleted: Boolean = false) {
	// hash ["blobId", "content"]
	// source: https://github.com/TriliumNext/Notes/blob/develop/src/becca/entities/bblob.ts
	Cache.registerEntityChange(
		"blobs",
		b.blobId,
		listOf(
			b.blobId.encodeToByteArray(),
			b.content
		),
		deleted
	)
}
