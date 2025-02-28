package eu.fliegendewurst.triliumdroid.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
import android.util.Log
import eu.fliegendewurst.triliumdroid.data.Blob
import eu.fliegendewurst.triliumdroid.database.Cache.db
import eu.fliegendewurst.triliumdroid.service.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.WeakHashMap

object Blobs {
	private const val TAG = "Blobs"

	private val blobCache: MutableMap<String, Blob> = WeakHashMap()

	suspend fun new(content: ByteArray?): Blob = withContext(Dispatchers.IO) {
		val blobId = Util.newNoteId()
		val dateModified = Cache.dateModified()
		val utcDateModified = Cache.utcDateModified()

		val cv = ContentValues()
		cv.put("blobId", blobId)
		cv.put("content", content ?: ByteArray(0))
		cv.put("dateModified", dateModified)
		cv.put("utcDateModified", utcDateModified)

		if (db!!.insertWithOnConflict("blobs", null, cv, CONFLICT_FAIL) == -1L) {
			Log.w(TAG, "failed to insert new blobId = $blobId")
			return@withContext new(content)
		}
		Cache.registerEntityChange(
			"blobs",
			blobId,
			listOf(blobId.encodeToByteArray(), content ?: ByteArray(0))
		)
		return@withContext Blob(blobId, content ?: ByteArray(0), dateModified, utcDateModified)
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
}
