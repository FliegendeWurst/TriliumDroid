package eu.fliegendewurst.triliumdroid.sync

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.InputStream

/** Applies a complete response and its checkpoint in one transaction. */
internal object SyncPullWriter {

	fun apply(db: SQLiteDatabase, input: InputStream, spoolDirectory: File): StreamingSyncReader.Summary {
		db.beginTransaction()
		try {
			val summary = StreamingSyncReader.read(input, spoolDirectory) { change ->
				applyChange(db, change)
			}
			val checkpoint = ContentValues().apply {
				put("name", "lastSyncedPull")
				put("value", summary.lastEntityChangeId.toString())
				put("isSynced", 0)
				put("utcDateModified", eu.fliegendewurst.triliumdroid.database.Cache.utcDateModified())
			}
			check(db.insertWithOnConflict("options", null, checkpoint, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
			db.setTransactionSuccessful()
			return summary
		} finally {
			db.endTransaction()
		}
	}

	private fun applyChange(db: SQLiteDatabase, change: Map<String, Any?>) {
		val metadata = change["entityChange"] as? Map<*, *> ?: error("Missing entity change metadata")
		val table = metadata["entityName"] as? String ?: error("Missing entity name")
		val entity = change["entity"] as? Map<*, *>
		val primaryKey = Sync.primaryKeyForTable(table)
		if (entity == null) {
			require(primaryKey.isNotEmpty()) { "Unsupported erased sync entity: $table" }
			db.delete(table, "$primaryKey = ?", arrayOf(metadata["entityId"].toString()))
			return
		}
		val alreadyHere = db.rawQuery(
			"SELECT 1 FROM entity_changes WHERE changeId = ?", arrayOf(metadata["changeId"].toString())
		).use { it.moveToFirst() }
		if (alreadyHere) return
		if (table == "note_reordering") {
			for ((key, position) in entity) {
				val values = ContentValues().apply { put("notePosition", (position as Number).toLong()) }
				db.update("branches", values, "branchId = ?", arrayOf(key.toString()))
			}
			return
		}
		require(primaryKey.isNotEmpty()) { "Unsupported sync entity: $table" }
		var contentFile: File? = null
		val values = ContentValues()
		for ((key, value) in entity) {
			val field = key as String
			when (value) {
				null -> values.putNull(field)
				is File -> {
					require(field == "content")
					contentFile = value
					values.put(field, byteArrayOf())
				}
				is String -> values.put(field, value)
				is Long -> values.put(field, value)
				is Int -> values.put(field, value)
				is Double -> values.put(field, value)
				is Boolean -> values.put(field, value)
				else -> error("Unsupported sync value for $table.$field")
			}
		}
		check(db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
			"Could not store sync entity: $table"
		}
		contentFile?.let { file ->
			val id = entity[primaryKey] as? String ?: error("Missing entity primary key")
			// Android's public SQLite API has no incremental blob handle. Append bounded chunks
			// inside the page transaction; CAST preserves embedded NUL bytes as a SQLite BLOB.
			db.compileStatement("UPDATE $table SET content = CAST(content || ? AS BLOB) WHERE $primaryKey = ?").use { statement ->
				val buffer = ByteArray(minOf(file.length(), 8L * 1024 * 1024).toInt().coerceAtLeast(1))
				file.inputStream().use { input ->
					var count = input.read(buffer)
					while (count != -1) {
						statement.bindBlob(1, if (count == buffer.size) buffer else buffer.copyOf(count))
						statement.bindString(2, id)
						check(statement.executeUpdateDelete() == 1)
						statement.clearBindings()
						count = input.read(buffer)
					}
				}
			}
			if (file.length() >= 16L * 1024 * 1024) Log.i("SyncPullWriter", "Stored large content: ${file.length()} bytes")
		}
	}
}
