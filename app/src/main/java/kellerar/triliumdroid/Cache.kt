package kellerar.triliumdroid

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import android.util.Log
import kellerar.triliumdroid.Cache.CacheDatabaseContract.BranchRow
import kellerar.triliumdroid.Cache.CacheDatabaseContract.NoteRow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.Exception
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.TreeMap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


class Cache {
	companion object {
		private val TAG: String = "Cache"

		public var notes: MutableMap<String, Note> = HashMap()
		public var branches: MutableMap<String, Branch> = HashMap()

		public var branchesDirty: MutableSet<String> = HashSet()

		private var dbHelper: CacheDbHelper? = null
		var db: SQLiteDatabase? = null

		fun getNote(activity: Activity, id: String): Note? {
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
			return note
		}

		fun insertBranch(branch: Branch) {
			return
			if (branches.containsKey(branch.note)) {
				branchesDirty.add(branch.id)
				for (entry in branch.children.entries) {
					branches[branch.note]!!.children[entry.key] = entry.value
				}
			} else {
				branches[branch.note] = branch
			}

			val values = ContentValues().apply {
				put(BranchRow._ID, branch.id)
				put(BranchRow.COLUMN_NAME_NOTE, branch.note)
				put(BranchRow.COLUMN_NAME_PARENT_NOTE, branch.parentNote)
				put(BranchRow.COLUMN_NAME_POSITION, branch.position)
				put(BranchRow.COLUMN_NAME_PREFIX, branch.prefix)
				put(BranchRow.COLUMN_NAME_EXPANDED, branch.expanded)
			}

			try {
				db!!.insertWithOnConflict(
					BranchRow.TABLE_NAME,
					null,
					values,
					SQLiteDatabase.CONFLICT_REPLACE
				)
			} catch (t: Throwable) {
				Log.e(TAG, "fatal", t)
			}
		}

		fun getTreeData(activity: Activity, id: String) {
			db!!.rawQuery(
				"SELECT branchId, noteId, parentNoteId, notePosition, prefix, isExpanded, mime, title FROM branches, notes WHERE branches.noteId = notes.noteId",
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

				ConnectionUtil.doSyncRequest(activity, changesUri) { resp ->
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
								// TODO
								return@use
							}
							if (arrayOf("note_contents", "note_revision_contents").contains(entityName)) {
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
			// This database is only a cache for online data, so its upgrade policy is
			// to simply to discard the data and start over
			try {
				db.execSQL(CacheDatabaseContract.SQL_DELETE_BRANCHES)
				db.execSQL(CacheDatabaseContract.SQL_DELETE_NOTES)
			} catch (t: Throwable) {
				Log.e(TAG, "fatal", t)
			}
			onCreate(db)
		}

		override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
			onUpgrade(db, oldVersion, newVersion)
		}

		companion object {
			// If you change the database schema, you must increment the database version.
			const val DATABASE_VERSION = 1
			const val DATABASE_NAME = "Document.db"
		}
	}


	object CacheDatabaseContract {
		public const val SQL_CREATE_BRANCHES =
			"CREATE TABLE ${BranchRow.TABLE_NAME} (" +
					"${BranchRow._ID} TEXT PRIMARY KEY," +
					"${BranchRow.COLUMN_NAME_NOTE} TEXT NOT NULL," +
					"${BranchRow.COLUMN_NAME_PARENT_NOTE} TEXT NOT NULL," +
					"${BranchRow.COLUMN_NAME_POSITION} INT NOT NULL," +
					"${BranchRow.COLUMN_NAME_PREFIX} TEXT," +
					"${BranchRow.COLUMN_NAME_EXPANDED} INT NOT NULL)"
		public const val SQL_DELETE_BRANCHES = "DROP TABLE ${BranchRow.TABLE_NAME}"

		public const val SQL_CREATE_NOTES =
			"CREATE TABLE ${NoteRow.TABLE_NAME} (" +
					"${NoteRow._ID} TEXT PRIMARY KEY," +
					"${NoteRow.COLUMN_NAME_TITLE} TEXT NOT NULL," +
					"${NoteRow.COLUMN_NAME_CONTENT} BLOB)"
		public const val SQL_DELETE_NOTES = "DROP TABLE ${NoteRow.TABLE_NAME}"

		object BranchRow : BaseColumns {
			const val TABLE_NAME = "branch"
			const val _ID = "id"
			const val COLUMN_NAME_NOTE = "note"
			const val COLUMN_NAME_PARENT_NOTE = "parentNote"
			const val COLUMN_NAME_POSITION = "position"
			const val COLUMN_NAME_PREFIX = "prefix"
			const val COLUMN_NAME_EXPANDED = "expanded"
		}

		object NoteRow : BaseColumns {
			const val TABLE_NAME = "note"
			const val _ID = "id"
			const val COLUMN_NAME_TITLE = "title"
			const val COLUMN_NAME_CONTENT = "content"
		}
	}
}