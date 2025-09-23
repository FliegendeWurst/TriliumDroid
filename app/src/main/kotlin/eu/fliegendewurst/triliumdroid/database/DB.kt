package eu.fliegendewurst.triliumdroid.database

import android.content.ContentValues
import android.content.Context
import android.database.AbstractWindowedCursor
import android.database.Cursor
import android.database.Cursor.FIELD_TYPE_STRING
import android.database.CursorWindow
import android.database.sqlite.SQLiteCursorDriver
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteQuery
import android.os.Build
import android.util.Log
import androidx.core.database.getStringOrNull
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.database.Branches.branches
import eu.fliegendewurst.triliumdroid.database.CacheDbHelper.Companion.MAX_MIGRATION
import eu.fliegendewurst.triliumdroid.database.Notes.notes
import eu.fliegendewurst.triliumdroid.util.Preferences
import eu.fliegendewurst.triliumdroid.util.Unreachable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object DB {
	private const val TAG = "DB"

	var applicationContext: Context? = null

	private var dbHelper: CacheDbHelper? = null
	private var db: SQLiteDatabase? = null

	var lastSync: Long? = null
	var skipNextMigration: Boolean = false

	/**
	 * @return -1 on error
	 */
	suspend fun insert(table: String, vararg columns: Pair<String, Any?>): Long {
		if (Preferences.readOnlyMode()) {
			Log.w(TAG, "read-only mode ignoring database insert!")
			return 0
		}
		ensureDatabase()
		val cv = valuesFromPairs(columns)
		return withContext(Dispatchers.IO) {
			db!!.insert(table, null, cv)
		}
	}

	/**
	 * @return -1 on error
	 */
	suspend fun insertWithConflict(
		table: String,
		onConflict: Int,
		vararg columns: Pair<String, Any?>
	): Long {
		if (Preferences.readOnlyMode()) {
			Log.w(TAG, "read-only mode ignoring database insert!")
			return 0
		}
		ensureDatabase()
		val cv = valuesFromPairs(columns)
		return withContext(Dispatchers.IO) {
			db!!.insertWithOnConflict(table, null, cv, onConflict)
		}
	}

	suspend fun update(selector: IdLike, vararg columns: Pair<String, Any?>) {
		ensureDatabase()
		val cv = valuesFromPairs(columns)
		withContext(Dispatchers.IO) {
			db!!.update(
				selector.tableName(),
				cv,
				"${selector.columnName()} = ?",
				arrayOf(selector.rawId())
			)
		}
	}

	suspend fun delete(table: String, primaryKey: String, selectionArgs: Array<String>) =
		withContext(Dispatchers.IO) {
			if (Preferences.readOnlyMode()) {
				Log.w(TAG, "read-only mode ignoring database delete!")
				return@withContext 0
			}
			ensureDatabase()
			db!!.delete(table, "$primaryKey = ?", selectionArgs)
		}

	/**
	 * Generate a new ID using the provided generator.
	 * Checks the database table for a match, and generates a fresh ID on collision.
	 */
	suspend fun newId(
		table: String,
		columnName: String,
		generator: () -> String
	): String = withContext(Dispatchers.IO) {
		if (Preferences.readOnlyMode()) {
			Log.w(TAG, "read-only mode ignoring database ID generation!")
			return@withContext "INVALID"
		}
		ensureDatabase()
		while (true) {
			val candidate = generator.invoke()
			db!!.rawQuery(
				"SELECT $columnName FROM $table WHERE $columnName = ?",
				arrayOf(candidate)
			).use {
				if (!it.moveToNext()) {
					return@withContext candidate
				}
			}
		}
		throw Unreachable()
	}

	suspend fun rawQuery(sql: String, selectionArgs: Array<String>): Cursor {
		ensureDatabase()
		return db!!.rawQuery(sql, selectionArgs)
	}

	suspend fun rawQueryWithFactory(
		factory: CursorFactory,
		sql: String,
		selectionArgs: Array<String>,
		editTable: String
	): Cursor {
		ensureDatabase()
		return db!!.rawQueryWithFactory(factory, sql, selectionArgs, editTable)
	}

	/**
	 * Make sure to respect [Preferences.readOnlyMode] when using the handle.
	 *
	 * @return raw database handle
	 */
	suspend fun internalGetDatabase(): SQLiteDatabase? {
		ensureDatabase()
		return db
	}

	private suspend fun ensureDatabase() {
		if (db?.isOpen == false) {
			db = null
		}
		if (db == null) {
			initializeDatabase(applicationContext!!)
		}
	}

	fun haveDatabase(context: Context): Boolean {
		if (db?.isOpen == true) {
			return true
		}
		val file = File(context.filesDir.parent, "databases/Document.db")
		return file.exists()
	}

	@OptIn(ExperimentalEncodingApi::class)
	suspend fun initializeDatabase(context: Context) = withContext(Dispatchers.IO) {
		if (db != null && db!!.isOpen) {
			return@withContext
		}
		Log.d(TAG, "initializing database")
		try {
			val sql = context.resources.openRawResource(R.raw.schema).bufferedReader()
				.use { it.readText() }
			dbHelper = CacheDbHelper(context, sql)
			db = dbHelper!!.writableDatabase
		} catch (t: Throwable) {
			Log.e(TAG, "fatal ", t)
			return@withContext
		}
		// perform migrations as needed
		val migrationLevel = Preferences.databaseMigration()
		if (migrationLevel < 1) {
			val decoded = mutableListOf<Pair<String, ByteArray>>()
			// base64-decode all blobs.content values
			CursorFactory.selectionArgs = arrayOf()
			db!!.rawQueryWithFactory(
				CursorFactory,
				"SELECT blobId, content FROM blobs",
				arrayOf(),
				"notes"
			).use {
				while (it.moveToNext()) {
					val id = it.getString(0)
					if (it.getType(1) != FIELD_TYPE_STRING) {
						continue
					}
					val content = it.getStringOrNull(1)
					if (content != null) {
						decoded.add(Pair(id, Base64.decode(content)))
					}
				}
			}
			for (p in decoded) {
				val cv = ContentValues()
				cv.put("content", p.second)
				db!!.update("blobs", cv, "blobId = ?", arrayOf(p.first))
			}
		}
		if (migrationLevel < 2) {
			Blobs.fixupBrokenBlobIDs()
		}
		Preferences.setDatabaseMigration(MAX_MIGRATION)
	}

	fun nukeDatabase(context: Context) {
		if (db != null && db!!.isOpen) {
			db!!.close()
			dbHelper?.close()
			db = null
			dbHelper = null
		}
		Preferences.clearSyncContext()
		File(context.filesDir.parent, "databases/Document.db").delete()
		notes.clear()
		branches.clear()
		lastSync = null
		// DB migrations are only for fixups
		Preferences.setDatabaseMigration(MAX_MIGRATION)
	}

	fun closeDatabase() {
		Log.d(TAG, "closing database")
		db?.close()
		dbHelper?.close()
	}

	/**
	 * Get database-ready CV object based on column name, value pairs
	 */
	fun valuesFromPairs(columns: Array<out Pair<String, Any?>>): ContentValues {
		val cv = ContentValues()
		for (x in columns) {
			val key = x.first
			val value = x.second
			if (value == null) {
				// no-op
				continue
			}
			when (value) {
				is String -> {
					cv.put(key, value)
				}

				is Byte -> {
					cv.put(key, value)
				}

				is Short -> {
					cv.put(key, value)
				}

				is Int -> {
					cv.put(key, value)
				}

				is Long -> {
					cv.put(key, value)
				}

				is Float -> {
					cv.put(key, value)
				}

				is Double -> {
					cv.put(key, value)
				}

				is Boolean -> {
					cv.put(key, value.boolToIntValue())
				}

				is ByteArray -> {
					cv.put(key, value)
				}

				is IdLike -> {
					cv.put(key, value.rawId())
				}

				else -> {
					val msg = "tried to insert wrong data type ${value.javaClass.name} in database"
					Log.e(TAG, msg)
					throw IllegalStateException("$msg, please report this bug!")
				}
			}
		}
		return cv
	}

	object CursorFactory : SQLiteDatabase.CursorFactory {
		var selectionArgs: Array<String> = emptyArray()

		override fun newCursor(
			db: SQLiteDatabase?,
			masterQuery: SQLiteCursorDriver?,
			editTable: String?,
			query: SQLiteQuery?
		): Cursor {
			val cursor =
				db?.rawQuery(
					query!!.toString().substring("SQLiteQuery: ".length),
					selectionArgs
				)!!
			// try 16 MB for note content
			val cw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				CursorWindow("note_content", 16 * 1024 * 1024)
			} else {
				CursorWindow("note_content")
			}
			(cursor as AbstractWindowedCursor).window = cw
			return cursor
		}

	}
}
