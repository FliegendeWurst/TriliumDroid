package eu.fliegendewurst.triliumdroid.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
import android.util.Log
import eu.fliegendewurst.triliumdroid.data.Blob
import eu.fliegendewurst.triliumdroid.database.Cache.dateModified
import eu.fliegendewurst.triliumdroid.database.Cache.db
import eu.fliegendewurst.triliumdroid.database.Cache.utcDateModified
import eu.fliegendewurst.triliumdroid.service.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.WeakHashMap

object Blobs {
	private const val TAG = "Blobs"

	private val blobCache: MutableMap<String, Blob> = WeakHashMap()

	suspend fun new(content: ByteArray?): Blob = withContext(Dispatchers.IO) {
		val blobId = Util.randomString(20)
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

	suspend fun update(blobId: String, newContent: ByteArray): Blob = withContext(Dispatchers.IO) {
		val cv = ContentValues()
		cv.put("content", newContent)
		val dateModified = dateModified()
		cv.put("dateModified", dateModified)
		val utcDateModified = utcDateModified()
		cv.put("utcDateModified", utcDateModified)
		db!!.update("blobs", cv, "blobId = ?", arrayOf(blobId))

		val previousBlob = blobCache[blobId]
		if (previousBlob == null) {
			val newBlob = Blob(blobId, newContent, dateModified, utcDateModified)
			blobCache[blobId] = newBlob
			registerEntityChangeBlob(newBlob)
			return@withContext newBlob
		} else {
			previousBlob.content = newContent
			previousBlob.dateModified = dateModified
			previousBlob.utcDateModified = utcDateModified
			registerEntityChangeBlob(previousBlob)
			return@withContext previousBlob
		}
	}

	suspend fun delete(blobId: String) = withContext(Dispatchers.IO) {
		// TODO: don't bother loading blob content again
		val blob = load(blobId) ?: return@withContext
		db!!.delete("blobs", "blobId = ?", arrayOf(blobId))
		registerEntityChangeBlob(
			Blob(
				blob.blobId,
				ByteArray(0),
				blob.dateModified,
				blob.utcDateModified
			),
			true
		)
		blobCache.remove(blobId)
		// TODO: mark note as invalid, if belonging to note
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
