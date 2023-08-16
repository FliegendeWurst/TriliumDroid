package kellerar.triliumdroid

import android.app.Activity
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONObject
import java.lang.Exception
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.TreeMap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


object Cache {
	private const val TAG: String = "Cache"

	private var notes: MutableMap<String, Note> = HashMap()
	private var branches: MutableMap<String, Branch> = HashMap()

	private var branchPosition: MutableMap<String, Int> = HashMap()

	private var dbHelper: CacheDbHelper? = null
	private var db: SQLiteDatabase? = null

	fun getBranchPosition(id: String): Int? {
		return branchPosition[id]
	}

	fun getNoteWithContent(id: String): Note? {
		if (notes.containsKey(id) && notes[id]!!.content != null) {
			return notes[id]
		}
		return getNoteInternal(id)
	}

	fun getNote(id: String): Note? {
		if (notes.containsKey(id)) {
			return notes[id]
		}
		return getNoteInternal(id)
	}

	private fun getNoteInternal(id: String): Note? {
		var note: Note? = null
		db!!.rawQuery(
			"SELECT content, mime, title FROM notes, note_contents WHERE notes.noteId = note_contents.noteId AND notes.noteId = ?",
			arrayOf(id)
		).use {
			if (it.moveToFirst()) {
				note = Note(id, it.getString(1), it.getString(2))
				note!!.content = it.getBlob(0)
			}
		}
		if (note != null) {
			notes[id] = note!!
		}
		return note
	}

	fun getJumpToResults(input: String): List<Note> {
		val notes = mutableListOf<Note>()
		db!!.rawQuery(
			"SELECT noteId, mime, title FROM notes WHERE title LIKE ? LIMIT 200",
			arrayOf("%$input%")
		).use {
			while (it.moveToNext()) {
				val note = Note(it.getString(0), it.getString(1), it.getString(2))
				notes.add(note)
			}
		}
		return notes
	}

	/**
	 * Populate the tree data cache.
	 */
	fun getTreeData() {
		db!!.rawQuery(
			"SELECT branchId, branches.noteId, parentNoteId, notePosition, prefix, isExpanded, mime, title FROM branches, notes WHERE branches.noteId = notes.noteId",
			arrayOf()
		).use {
			while (it.moveToNext()) {
				val branchId = it.getString(0)
				val noteId = it.getString(1)
				val parentNoteId = it.getString(2)
				val notePosition = it.getInt(3)
				val prefix = if (!it.isNull(4)) {
					it.getString(4)
				} else {
					null
				}
				val isExpanded = it.getInt(5) == 1
				val mime = it.getString(6)
				val title = it.getString(7)
				branches[noteId] = Branch(
					branchId,
					noteId,
					parentNoteId,
					notePosition,
					prefix,
					isExpanded,
					TreeMap()
				)
				notes[noteId] = Note(noteId, mime, title)
			}
			for (branchId in branches.keys) {
				val b = branches[branchId]!!
				branches[b.parentNote]?.children?.set(b.position, b)
			}
		}
	}

	/**
	 * Get the note tree starting at the id and level.
	 */
	fun getTreeList(id: String, lvl: Int): MutableList<Pair<Branch, Int>> {
		val list = ArrayList<Pair<Branch, Int>>()
		val current = branches[id] ?: return list
		list.add(Pair(current, lvl))
		for (children in current.children.values) {
			list.addAll(getTreeList(children.note, lvl + 1))
		}
		for ((i, pair) in list.withIndex()) {
			branchPosition[pair.first.note] = i
		}
		return list
	}

	@OptIn(ExperimentalEncodingApi::class)
	fun sync(activity: Activity, callback: () -> Unit) {
		try {
			var lastSyncedPull = 0;
			db!!.rawQuery("SELECT value FROM options WHERE name = ?", arrayOf("lastSyncedPull"))
				.use {
					if (it.moveToFirst()) {
						lastSyncedPull = it.getInt(0)
					}
				}
			val logMarkerId = "trilium-droid"
			val instanceId = "trilium-droid-1"
			val changesUri =
				"/api/sync/changed?instanceId=${instanceId}&lastEntityChangeId=${lastSyncedPull}&logMarkerId=${logMarkerId}";

			ConnectionUtil.doSyncRequest(changesUri) { resp ->
				val outstandingPullCount = resp.getInt("outstandingPullCount")
				val entityChangeId = resp.getString("lastEntityChangeId")
				Log.i(TAG, "sync outstanding $outstandingPullCount")
				val changes = resp.getJSONArray("entityChanges")
				for (i in 0 until changes.length()) {
					val change = changes.get(i) as JSONObject
					val entityChange = change.optJSONObject("entityChange")!!
					val entityName = entityChange.getString("entityName")
					val changeId = entityChange.getString("changeId")
					var entity = change.optJSONObject("entity") ?: continue
					db!!.rawQuery(
						"SELECT 1 FROM entity_changes WHERE changeId = ?",
						arrayOf(changeId)
					).use {
						if (it.count != 0) {
							return@use
						}
						if (entityName == "note_reordering") {
							for (key in entity.keys()) {
								db!!.execSQL(
									"UPDATE branches SET notePosition = ? WHERE branchId = ?",
									arrayOf(entity[key], key)
								);
							}
							return@use
						}
						if (arrayOf("note_contents", "note_revision_contents").contains(
								entityName
							)
						) {
							entity.put("content", Base64.decode(entity.getString("content")))
						}
						val keys = entity.keys().asSequence().toList()

						val columns = keys.joinToString(", ")
						val questionMarks = keys.map { "?" }.joinToString(", ")

						val query =
							"INSERT OR REPLACE INTO $entityName ($columns) VALUES ($questionMarks)"
						//Log.d(TAG, "sync: inserting one $entityName")
						db!!.execSQL(query, keys.map { entity.get(it) }.toTypedArray())
					}
				}
				Log.i(TAG, "last entity change id: $entityChangeId")
				val utc =
					DateTimeFormatter.ISO_INSTANT.format(OffsetDateTime.now(ZoneOffset.UTC))
						.replace('T', ' ')
				db!!.execSQL(
					"INSERT OR REPLACE INTO options (name, value, utcDateModified) VALUES (?, ?, ?)",
					arrayOf("lastSyncedPull", entityChangeId, utc)
				)
				if (outstandingPullCount > 0) {
					sync(activity, callback)
				} else {
					callback()
				}
			}
		} catch (e: Exception) {
			Log.e(TAG, "sync error", e)
		}
	}

	fun initializeDatabase(context: Context) {
		try {
			val sql = context.resources.openRawResource(R.raw.schema).bufferedReader()
				.use { it.readText() }
			dbHelper = CacheDbHelper(context, sql)
			db = dbHelper!!.writableDatabase
		} catch (t: Throwable) {
			Log.e(TAG, "fatal", t)
		}
	}

	fun closeDatabase() {
		db?.close()
		dbHelper?.close()
	}

	class CacheDbHelper(context: Context, private val sql: String) :
		SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

		override fun onCreate(db: SQLiteDatabase) {
			try {
				Log.i(TAG, "creating database ${db.attachedDbs[0].second}")
				sql.split(';').forEach {
					db.execSQL(it)
				}
				db.rawQuery(
					"SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name != 'android_metadata' AND name != 'sqlite_sequence'",
					arrayOf()
				).use {
					Log.i(TAG, "successfully created ${it.count} tables")
				}
			} catch (t: Throwable) {
				Log.e(TAG, "fatal error creating database", t)
			}
		}

		override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
			// TODO: execute the migrations
			try {
				//db.execSQL("...")
			} catch (t: Throwable) {
				Log.e(TAG, "fatal", t)
			}
		}

		override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
			// TODO: do something here
		}

		companion object {
			// If you change the database schema, you must increment the database version.
			const val DATABASE_VERSION = 1
			const val DATABASE_NAME = "Document.db"
		}
	}
}