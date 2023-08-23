package eu.fliegendewurst.triliumdroid

import android.annotation.SuppressLint
import android.content.Context
import android.database.AbstractWindowedCursor
import android.database.Cursor
import android.database.CursorWindow
import android.database.sqlite.SQLiteCursorDriver
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteQuery
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.util.Log
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.Label
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.Relation
import org.json.JSONArray
import org.json.JSONObject
import java.lang.StrictMath.max
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


@OptIn(ExperimentalEncodingApi::class)
object Cache {
	private const val TAG: String = "Cache"

	@SuppressLint("SimpleDateFormat")
	private val localTime: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ")

	private var notes: MutableMap<String, Note> = HashMap()
	private var branches: MutableMap<String, Branch> = HashMap()

	private var branchPosition: MutableMap<String, Int> = HashMap()

	private var dbHelper: CacheDbHelper? = null
	private var db: SQLiteDatabase? = null

	fun getBranchPosition(id: String): Int? {
		return branchPosition[id]
	}

	fun getNoteWithContent(id: String): Note? {
		if (notes.containsKey(id) && notes[id]!!.mime != "INVALID" && notes[id]!!.content != null) {
			return notes[id]
		}
		return getNoteInternal(id)
	}

	fun setNoteContent(id: String, content: String) {
		if (!notes.containsKey(id)) {
			getNoteInternal(id)
		}
		val data = content.encodeToByteArray()
		notes[id]!!.content = data
		val date = localTime.format(Calendar.getInstance().time)
		val utc = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
		db!!.execSQL(
			"UPDATE note_contents SET content = ?, dateModified = ?, utcDateModified = ? WHERE note_contents.noteId = ?",
			arrayOf(notes[id]!!.content, date, utc, id)
		)
		val md = MessageDigest.getInstance("SHA-1")
		md.update(data, 0, data.size)
		val sha1hash = md.digest()
		val hash = Base64.encode(sha1hash)
		db!!.execSQL(
			"INSERT OR REPLACE INTO entity_changes (entityName, entityId, hash, isErased, changeId, componentId, instanceId, isSynced, utcDateChanged) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
			arrayOf(
				"note_contents",
				id,
				hash,
				0,
				"changeId${(1000..9999).random()}",
				"NA",
				"mobilemobile",
				1,
				utc
			)
		)
	}

	fun getNotePath(id: String): List<Branch> {
		val l = mutableListOf<Branch>()
		var lastId = id
		while (true) {
			db!!.rawQuery(
				"SELECT branchId, parentNoteId, isExpanded FROM branches WHERE noteId = ? LIMIT 1",
				arrayOf(lastId)
			).use {
				if (it.moveToNext()) {
					val branchId = it.getString(0)
					val parentId = it.getString(1)
					val expanded = it.getInt(2) == 1
					l.add(Branch(branchId, lastId, parentId, 0, null, expanded, TreeMap()))
					if (parentId == "none") {
						return l
					}
					lastId = parentId
				} else {
					return l
				}
			}
		}
	}

	fun toggleBranch(branch: Branch) {
		db!!.execSQL(
			"UPDATE branches SET isExpanded = ? WHERE branchId = ?",
			arrayOf(
				if (branch.expanded) {
					0
				} else {
					1
				}, branch.id
			)
		)
		val newValue = !branch.expanded
		branch.expanded = newValue
		branches[branch.note]?.expanded = newValue
	}

	fun getNote(id: String): Note? {
		if (notes.containsKey(id) && notes[id]?.mime != "INVALID") {
			return notes[id]
		}
		return getNoteInternal(id)
	}

	private fun getNoteInternal(id: String): Note? {
		var note: Note? = null
		CursorFactory.selectionArgs = arrayOf(id)
		db!!.rawQueryWithFactory(
			CursorFactory,
			"SELECT content, mime, title, attributes.type, attributes.name, attributes.value, notes.type FROM notes, note_contents LEFT JOIN attributes USING(noteId) WHERE notes.noteId = note_contents.noteId AND notes.noteId = ?",
			arrayOf(id),
			"notes"
		).use {
			val labels = mutableListOf<Label>()
			val relations = mutableListOf<Relation>()
			if (it.moveToFirst()) {
				note = Note(id, it.getString(1), it.getString(2), it.getString(6))
				note!!.content = it.getBlob(0)
			}
			while (!it.isAfterLast) {
				if (!it.isNull(3)) {
					val type = it.getString(3)
					if (type == "label") {
						val name = it.getString(4)
						val value = it.getString(5)
						labels.add(Label(note!!, name, value))
					} else if (type == "relation") {
						val name = it.getString(4)
						// value = note ID
						val value = it.getString(5)
						if (!notes.containsKey(value)) {
							notes[value] = Note(value, "INVALID", "INVALID", "INVALID")
						}
						relations.add(Relation(note!!, notes[value], name))
					}
				}
				it.moveToNext()
				note!!.labels = labels
				note!!.relations = relations
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
			"SELECT noteId, mime, title, type FROM notes WHERE title LIKE ? LIMIT 50",
			arrayOf("%$input%")
		).use {
			while (it.moveToNext()) {
				val note = Note(it.getString(0), it.getString(1), it.getString(2), it.getString(3))
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
			"SELECT branchId, branches.noteId, parentNoteId, notePosition, prefix, isExpanded, mime, title, notes.type FROM branches, notes WHERE branches.noteId = notes.noteId",
			arrayOf()
		).use {
			val clones = mutableListOf<Triple<String, String, Int>>()
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
				val type = it.getString(8)
				branches[noteId] = Branch(
					branchId,
					noteId,
					parentNoteId,
					notePosition,
					prefix,
					isExpanded,
					TreeMap()
				)
				notes[noteId] = Note(noteId, mime, title, type)
				clones.add(Triple(parentNoteId, noteId, notePosition))
			}
			for (p in clones) {
				val b = branches[p.second]!!
				branches[p.first]?.children?.set(p.third, b)
			}
		}
	}

	fun getBranch(noteId: String): Branch? {
		return branches[noteId]
	}

	/**
	 * Get the note tree starting at the id and level.
	 */
	fun getTreeList(id: String, lvl: Int): MutableList<Pair<Branch, Int>> {
		val list = ArrayList<Pair<Branch, Int>>()
		val current = branches[id] ?: return list
		list.add(Pair(current, lvl))
		if (!current.expanded) {
			return list
		}
		for (children in current.children.values) {
			list.addAll(getTreeList(children.note, lvl + 1))
		}
		for ((i, pair) in list.withIndex()) {
			branchPosition[pair.first.note] = i
		}
		return list
	}

	private fun syncPush(): Int {
		var lastSyncedPush = 0
		db!!.rawQuery("SELECT value FROM options WHERE name = ?", arrayOf("lastSyncedPush"))
			.use {
				if (it.moveToFirst()) {
					lastSyncedPush = it.getInt(0)
				}
			}
		Log.i(TAG, "last synced push: $lastSyncedPush")
		val logMarkerId = "trilium-droid"
		val instanceId = "mobilemobile"
		val changesUri = "/api/sync/update?logMarkerId=$logMarkerId"

		val data = JSONObject()
		data.put("instanceId", instanceId)
		val entities = JSONArray()
		var largestId = lastSyncedPush
		db!!.rawQuery(
			"SELECT * FROM entity_changes WHERE isSynced = 1 AND id > ?",
			arrayOf(lastSyncedPush.toString())
		).use {

			while (it.moveToNext()) {
				val instanceIdSaved = it.getString(7)
				if (instanceIdSaved != instanceId) {
					continue
				}

				val id = it.getInt(0)
				largestId = max(largestId, id)
				val entityName = it.getString(1)
				val entityId = it.getString(2)
				val hash = it.getString(3)
				val isErased = it.getInt(4)
				val changeId = it.getString(5)
				val componentId = it.getString(6)
				val isSynced = it.getString(8)
				val utc = it.getString(9)

				val row = JSONObject()

				val entityChange = JSONObject()
				entityChange.put("id", id)
				entityChange.put("entityName", entityName)
				entityChange.put("entityId", entityId)
				entityChange.put("hash", hash)
				entityChange.put("isErased", isErased)
				entityChange.put("changeId", changeId)
				entityChange.put("componentId", componentId)
				entityChange.put("instanceId", instanceId)
				entityChange.put("isSynced", isSynced)
				entityChange.put("utcDateChanged", utc)
				row.put("entityChange", entityChange)

				val entity = fetchEntity(entityName, entityId)
				row.put("entity", entity)

				entities.put(row)
			}
		}
		data.put("entities", entities)

		if (entities.length() > 0) {
			ConnectionUtil.doSyncPushRequest(changesUri, data)
			Log.i(TAG, "synced up to $largestId")
			val utc =
				DateTimeFormatter.ISO_INSTANT.format(OffsetDateTime.now(ZoneOffset.UTC))
					.replace('T', ' ')
			db!!.execSQL(
				"INSERT OR REPLACE INTO options (name, value, isSynced, utcDateModified) VALUES (?, ?, 0, ?)",
				arrayOf("lastSyncedPush", largestId.toString(), utc)
			)
		}
		return entities.length()
	}

	private fun fetchEntity(entityName: String, entityId: String): JSONObject {
		var keyName = entityName.substring(0, entityName.length - 1)
		if (keyName == "note_content") {
			keyName = "note"
		}
		val obj = JSONObject()
		db!!.rawQuery("SELECT * FROM $entityName WHERE ${keyName}Id = ?", arrayOf(entityId)).use {
			if (it.moveToNext()) {
				for (i in (0 until it.columnCount)) {
					var x: Any? = null
					when (it.getType(i)) {
						Cursor.FIELD_TYPE_NULL -> {
							x = null
						}

						Cursor.FIELD_TYPE_BLOB -> {
							x = it.getBlob(i)
						}

						Cursor.FIELD_TYPE_FLOAT -> {
							x = it.getDouble(i)
						}

						Cursor.FIELD_TYPE_INTEGER -> {
							x = it.getLong(i)
						}

						Cursor.FIELD_TYPE_STRING -> {
							x = it.getString(i)
						}
					}
					if (x is ByteArray) {
						obj.put(it.getColumnName(i), Base64.encode(x))
					} else {
						obj.put(it.getColumnName(i), x)
					}
				}
			}
		}
		return obj
	}

	fun sync(
		callbackOutstanding: (Int) -> Unit,
		callbackError: (Exception) -> Unit,
		callbackDone: (Pair<Int, Int>) -> Unit
	) {
		try {
			val totalPushed = syncPush()
			var totalSynced = 0
			var lastSyncedPull = 0
			db!!.rawQuery("SELECT value FROM options WHERE name = ?", arrayOf("lastSyncedPull"))
				.use {
					if (it.moveToFirst()) {
						lastSyncedPull = it.getInt(0)
					}
				}
			val logMarkerId = "trilium-droid"
			val instanceId = "mobilemobile"
			val changesUri =
				"/api/sync/changed?instanceId=${instanceId}&lastEntityChangeId=${lastSyncedPull}&logMarkerId=${logMarkerId}"

			ConnectionUtil.doSyncRequest(changesUri) { resp ->
				val outstandingPullCount = resp.getInt("outstandingPullCount")
				val entityChangeId = resp.getString("lastEntityChangeId")
				Log.i(TAG, "sync outstanding $outstandingPullCount")
				val changes = resp.getJSONArray("entityChanges")
				totalSynced += changes.length()
				for (i in 0 until changes.length()) {
					val change = changes.get(i) as JSONObject
					val entityChange = change.optJSONObject("entityChange")!!
					val entityName = entityChange.getString("entityName")
					val changeId = entityChange.getString("changeId")
					val entity = change.optJSONObject("entity") ?: continue
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
								)
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
					callbackOutstanding(outstandingPullCount)
					sync(callbackOutstanding, callbackError, callbackDone)
				} else {
					callbackDone(Pair(totalSynced, totalPushed))
				}
			}
		} catch (e: Exception) {
			Log.e(TAG, "sync error", e)
			callbackError(e)
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

	object CursorFactory : SQLiteDatabase.CursorFactory {
		var selectionArgs: Array<String> = emptyArray()

		override fun newCursor(
			db: SQLiteDatabase?,
			masterQuery: SQLiteCursorDriver?,
			editTable: String?,
			query: SQLiteQuery?
		): Cursor {
			val cursor =
				db?.rawQuery(query!!.toString().substring("SQLiteQuery: ".length), selectionArgs)!!
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