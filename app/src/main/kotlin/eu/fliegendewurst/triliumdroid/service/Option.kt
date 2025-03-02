package eu.fliegendewurst.triliumdroid.service

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import eu.fliegendewurst.triliumdroid.database.Cache
import eu.fliegendewurst.triliumdroid.database.Cache.db
import eu.fliegendewurst.triliumdroid.database.Cache.utcDateModified
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object Option {
	suspend fun passwordDerivedKeySalt() = getString("passwordDerivedKeySalt")
	suspend fun encryptedDataKey() = getString("encryptedDataKey").let {
		if (it != null) {
			Base64.decode(it.encodeToByteArray())
		} else {
			null
		}
	}

	suspend fun revisionInterval() = getInt("revisionSnapshotTimeInterval")
	suspend fun revisionIntervalUpdate(new: Int) = putInt("revisionSnapshotTimeInterval", new)

	private suspend fun getString(name: String): String? = withContext(Dispatchers.IO) {
		Cache.db!!.rawQuery("SELECT value FROM options WHERE name = ?", arrayOf(name))
			.use {
				if (it.moveToFirst()) {
					return@withContext it.getString(0)
				}
			}
		return@withContext null
	}

	private suspend fun getInt(name: String): Int? = getString(name)?.toInt()

	private suspend fun putString(name: String, value: String) = withContext(Dispatchers.IO) {
		val cv = ContentValues()
		cv.put("name", name)
		cv.put("value", value)
		cv.put("utcDateModified", utcDateModified())
		// TODO: consider syncing?
		db!!.insertWithOnConflict("options", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
	}

	private suspend fun putInt(name: String, value: Int) = putString(name, value.toString())
}
