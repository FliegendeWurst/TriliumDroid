package eu.fliegendewurst.triliumdroid.service

import android.database.sqlite.SQLiteDatabase
import eu.fliegendewurst.triliumdroid.database.Cache.utcDateModified
import eu.fliegendewurst.triliumdroid.database.DB
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
		DB.rawQuery("SELECT value FROM options WHERE name = ?", arrayOf(name))
			.use {
				if (it.moveToFirst()) {
					return@withContext it.getString(0)
				}
			}
		return@withContext null
	}

	private suspend fun getInt(name: String): Int? = getString(name)?.toInt()

	private suspend fun putString(name: String, value: String) = withContext(Dispatchers.IO) {
		// TODO: consider syncing?
		DB.insertWithConflict(
			"options", SQLiteDatabase.CONFLICT_REPLACE,
			Pair("name", name),
			Pair("value", value),
			Pair("utcDateModified", utcDateModified())
		)
	}

	private suspend fun putInt(name: String, value: Int) = putString(name, value.toString())
}
