package eu.fliegendewurst.triliumdroid.database

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.AbstractWindowedCursor
import android.database.Cursor
import android.database.CursorWindow
import android.database.sqlite.SQLiteCursorDriver
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteQuery
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.util.Log
import androidx.core.database.getBlobOrNull
import androidx.core.database.getStringOrNull
import androidx.core.database.sqlite.transaction
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.data.Attachment
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.Relation
import eu.fliegendewurst.triliumdroid.database.Branches.branches
import eu.fliegendewurst.triliumdroid.database.Notes.notes
import eu.fliegendewurst.triliumdroid.service.Icon
import eu.fliegendewurst.triliumdroid.service.Util
import eu.fliegendewurst.triliumdroid.util.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.TreeSet
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object Cache {
	private const val TAG: String = "Cache"

	@SuppressLint("SimpleDateFormat")
	val localTime: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ")

	private var dbHelper: CacheDbHelper? = null
	var db: SQLiteDatabase? = null
		private set

	var lastSync: Long? = null

	suspend fun registerEntityChange(
		table: String,
		id: String,
		toHash: Array<String>,
		isErased: Boolean = false
	) {
		registerEntityChange(table, id, toHash.map { it.encodeToByteArray() }, isErased)
	}

	suspend fun registerEntityChange(
		table: String,
		id: String,
		toHash: List<ByteArray>,
		isErased: Boolean = false
	) = withContext(Dispatchers.IO) {
		val utc = utcDateModified()
		val md = MessageDigest.getInstance("SHA-1")
		for (h in toHash) {
			md.update('|'.code.toByte())
			md.update(h, 0, h.size)
		}
		val sha1hash = md.digest()
		val hash = Base64.encode(sha1hash)
		db!!.execSQL(
			"INSERT OR REPLACE INTO entity_changes (entityName, entityId, hash, isErased, changeId, componentId, instanceId, isSynced, utcDateChanged) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
			arrayOf(
				table,
				id,
				hash,
				isErased.boolToIntValue(),
				Util.randomString(12),
				"Android",
				Preferences.instanceId(),
				1,
				utc
			)
		)
	}

	suspend fun getNotesWithAttribute(attributeName: String, attributeValue: String?): List<Note> =
		withContext(Dispatchers.IO) {
			var query =
				"SELECT noteId FROM notes INNER JOIN attributes USING (noteId) WHERE attributes.name = ? AND attributes.isDeleted = 0"
			if (attributeValue != null) {
				query += " AND attributes.value = ?"
			}
			db!!.rawQuery(
				query,
				if (attributeValue != null) {
					arrayOf(attributeName, attributeValue)
				} else {
					arrayOf(attributeName)
				}
			).use {
				val l = mutableListOf<Note>()
				while (it.moveToNext()) {
					l.add(Notes.getNote(it.getString(0))!!)
				}
				return@withContext l
			}
		}

	suspend fun getAttachmentWithContent(id: String): Attachment? {
		return getAttachmentInternal(id)
	}

	private suspend fun getAttachmentInternal(id: String): Attachment? =
		withContext(Dispatchers.IO) {
			var note: Attachment? = null
			CursorFactory.selectionArgs = arrayOf(id)
			db!!.rawQueryWithFactory(
				CursorFactory,
				"SELECT content," + // 0
						"mime " + // 1
						"FROM attachments LEFT JOIN blobs USING (blobId) " +
						"WHERE attachments.attachmentId = ? AND attachments.isDeleted = 0",
				arrayOf(id),
				"notes"
			).use {
				if (it.moveToFirst()) {
					note = Attachment(
						id,
						it.getString(1),
					)
					note!!.content = it.getBlobOrNull(0)
				}
			}
			return@withContext note
		}

	suspend fun getJumpToResults(input: String): List<Note> = withContext(Dispatchers.IO) {
		val notes = mutableListOf<Note>()
		db!!.rawQuery(
			"SELECT noteId, mime, title, type FROM notes WHERE isDeleted = 0 AND title LIKE ? LIMIT 50",
			arrayOf("%$input%")
		).use {
			while (it.moveToNext()) {
				val note = Note(
					it.getString(0),
					it.getString(1),
					it.getString(2),
					it.getString(3),
					"INVALID",
					"INVALID",
					"INVALID",
					"INVALID",
					false,
					"INVALID"
				)
				notes.add(note)
			}
		}
		return@withContext notes
	}

	/**
	 * Populate the tree data cache.
	 */
	suspend fun getTreeData(filter: String) = withContext(Dispatchers.IO) {
		val startTime = System.currentTimeMillis()
		db!!.rawQuery(
			"SELECT branchId, " +
					"branches.noteId, " +
					"parentNoteId, " +
					"notePosition, " +
					"prefix, " +
					"isExpanded, " +
					"mime, " +
					"title, " +
					"notes.type, " +
					"notes.dateCreated, " +
					"notes.dateModified, " +
					"notes.isProtected, " +
					"notes.utcDateCreated, " +
					"notes.utcDateModified, " +
					"notes.blobId " +
					"FROM branches INNER JOIN notes USING (noteId) WHERE notes.isDeleted = 0 AND branches.isDeleted = 0 $filter",
			arrayOf()
		).use {
			val clones = mutableListOf<Pair<Pair<String, String>, String>>()
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
				val dateCreated = it.getString(9)
				val dateModified = it.getString(10)
				val isProtected = it.getInt(11) != 0
				val utcDateCreated = it.getString(12)
				val utcDateModified = it.getString(13)
				val blobId = it.getString(14)
				val b = Branch(
					branchId,
					noteId,
					parentNoteId,
					notePosition,
					prefix,
					isExpanded
				)
				branches[branchId] = b
				val n = notes.computeIfAbsent(noteId) {
					Note(
						noteId,
						mime,
						title,
						type,
						dateCreated,
						dateModified,
						utcDateCreated,
						utcDateModified,
						isProtected,
						blobId
					)
				}
				if (n.branches.none { branch -> branch.id == branchId }) {
					n.branches.add(b)
					n.branches.sortBy { br -> br.position } // TODO: sort by date instead?
				}
				clones.add(Pair(Pair(parentNoteId, noteId), branchId))
			}
			for (p in clones) {
				val parentNoteId = p.first.first
				val b = branches[p.second]
				if (parentNoteId == "none") {
					continue
				}
				val parentNote = notes[parentNoteId]
				if (parentNote != null) {
					if (parentNote.children == null) {
						parentNote.children = TreeSet()
					}
					parentNote.children!!.add(b)
				} else {
					Log.w(TAG, "getTreeData() failed to find $parentNoteId in ${notes.size} notes")
				}
			}
		}
		val query1 = System.currentTimeMillis() - startTime
		val query2Start = System.currentTimeMillis()
		val stats = if (filter == "") {
			mutableMapOf<String, Int>()
		} else {
			null
		}
		db!!.rawQuery(
			"SELECT notes.noteId, attributes.value " +
					"FROM attributes " +
					"INNER JOIN notes USING (noteId) " +
					"INNER JOIN branches USING (noteId) " +
					"WHERE notes.isDeleted = 0 AND attributes.isDeleted = 0 " +
					"AND attributes.name = 'iconClass' AND attributes.type = 'label' $filter",
			arrayOf()
		).use {
			while (it.moveToNext()) {
				val noteId = it.getString(0)
				val noteIcon = it.getString(1)
				notes[noteId]!!.icon = noteIcon
				// gather statistics on note icons of user notes
				if (stats != null && Util.isRegularId(noteId)) {
					stats[noteIcon] = stats.getOrDefault(noteIcon, 0) + 1
				}
			}
		}
		if (stats != null) {
			Icon.iconStatistics =
				stats.entries.sortedWith(Comparator.comparingInt<MutableMap.MutableEntry<String, Int>?> { -it.value }
					.thenComparing { it -> it.key })
					.map { Pair(Icon.getUnicodeCharacter(it.key), it.value) }
		}
		val query2 = System.currentTimeMillis() - query2Start
		if (query1 + query2 > 50) {
			Log.w(TAG, "slow getTreeData() $query1 ms + $query2 ms")
		}
	}

	/**
	 * Use [Note.computeChildren] instead
	 */
	suspend fun getChildren(noteId: String) {
		getTreeData("AND (branches.parentNoteId = '${noteId}' OR branches.noteId = '${noteId}')")
	}

	/**
	 * Get the note tree starting at the id and level.
	 */
	fun getTreeList(branchId: String, lvl: Int): MutableList<Pair<Branch, Int>> {
		val list = ArrayList<Pair<Branch, Int>>()
		val current = branches[branchId] ?: return list
		list.add(Pair(current, lvl))
		if (!current.expanded) {
			return list
		}
		for (children in notes[current.note]!!.children.orEmpty()) {
			list.addAll(getTreeList(children.id, lvl + 1))
		}
		return list
	}

	fun haveDatabase(context: Context): Boolean {
		if (db?.isOpen == true) {
			return true
		}
		val file = File(context.filesDir.parent, "databases/Document.db")
		return file.exists()
	}

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
		val decoded = mutableListOf<Pair<String, ByteArray>>()
		if (migrationLevel < 1) {
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
		Preferences.setDatabaseMigration(1)
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
		Preferences.setDatabaseMigration(1)
	}

	fun closeDatabase() {
		Log.d(TAG, "closing database")
		db?.close()
		dbHelper?.close()
	}

	/**
	 * Get all notes with their relations.
	 * WARNING: the returned notes ONLY have their title and relations set.
	 */
	suspend fun getAllNotesWithRelations(): List<Note> = withContext(Dispatchers.IO) {
		val list = mutableListOf<Note>()
		val relations = mutableListOf<Triple<String, String, Pair<String, String>>>()
		db!!.rawQuery(
			"SELECT " +
					"noteId, " + // 0
					"title," + // 1
					"attributes.name," + // 2
					"attributes.value," + // 3
					"attributes.attributeId " + // 4
					"FROM notes " +
					"LEFT JOIN attributes USING (noteId) " +
					"WHERE notes.isDeleted = 0 " +
					"AND (attributes.isDeleted = 0 OR attributes.isDeleted IS NULL) " +
					"AND (attributes.type == 'relation' OR attributes.type IS NULL) " +
					"AND SUBSTR(noteId, 1, 1) != '_' " +
					"ORDER BY noteId",
			arrayOf()
		).use {
			var currentNote: Note? = null
			// source, target, name
			while (it.moveToNext()) {
				val id = it.getString(0)
				val title = it.getString(1)
				val attrName = it.getString(2)
				val attrValue = it.getString(3)
				val attrId = it.getString(4)
				if (currentNote == null || currentNote.id != id) {
					if (currentNote != null) {
						list.add(currentNote)
					}
					currentNote = Note(id, "", title, "", "", "", "", "", false, "")
				}
				if (attrValue != null && !attrValue.startsWith('_') && !attrName.startsWith(
						"child:"
					)
				) {
					relations.add(Triple(id, attrValue, Pair(attrName, attrId)))
				}
			}
			if (currentNote != null) {
				list.add(currentNote)
			}
		}
		val notesById = list.associateBy { x -> x.id }
		val relationsById = mutableMapOf<String, MutableList<Relation>>()
		val relationsByIdIncoming = mutableMapOf<String, MutableList<Relation>>()
		for (rel in relations) {
			if (relationsById[rel.first] == null) {
				relationsById[rel.first] = mutableListOf()
			}
			if (relationsByIdIncoming[rel.second] == null) {
				relationsByIdIncoming[rel.second] = mutableListOf()
			}
			if (!notesById.containsKey(rel.second)) {
				// relation points to deleted note??
				Log.w(TAG, "relation from ${rel.first} to deleted ${rel.second} found")
				continue
			}
			val relation = Relation(
				rel.third.second,
				notesById[rel.second]!!, rel.third.first,
				inheritable = false,
				promoted = false,
				multi = false
			)
			relationsById[rel.first]!!.add(relation)
			relationsByIdIncoming[rel.second]!!.add(relation)
		}
		for (v in relationsById) {
			notesById[v.key]!!.setRelations(v.value)
			notesById[v.key]!!.incomingRelations = relationsByIdIncoming[v.key]
		}
		return@withContext list
	}

	fun dateModified(): String {
		return localTime.format(Calendar.getInstance().time)
	}

	/**
	 * Get time formatted as YYYY-MM-DD HH:MM:SS.sssZ
	 */
	fun utcDateModified(): String {
		return DateTimeFormatter.ISO_INSTANT.format(OffsetDateTime.now(ZoneOffset.UTC))
			.replace('T', ' ')
	}

	private class CacheDbHelper(context: Context, private val sql: String) :
		SQLiteOpenHelper(context, Versions.DATABASE_NAME, null, Versions.DATABASE_VERSION) {

		override fun onCreate(db: SQLiteDatabase) {
			try {
				Log.i(TAG, "creating database ${db.attachedDbs[0].second}")
				sql.split(';').forEach {
					if (it.isBlank()) {
						return@forEach
					}
					try {
						db.execSQL(it)
					} catch (e: SQLiteException) {
						Log.e(TAG, "failure in DB creation ", e)
					}
				}
				db.rawQuery(
					"SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name != 'android_metadata' AND name != 'sqlite_sequence'",
					arrayOf()
				).use {
					if (it.moveToNext()) {
						Log.i(TAG, "successfully created ${it.getInt(0)} tables")
					} else {
						Log.w(TAG, "unable to fetch sqlite_master table data")
					}
				}
				// DB migrations are only for fixups
				Preferences.setDatabaseMigration(1)
			} catch (t: Throwable) {
				Log.e(TAG, "fatal error creating database", t)
			}
		}

		override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
			try {
				// source: https://github.com/zadam/trilium/tree/master/db/migrations
				// and: https://github.com/TriliumNext/Notes/tree/develop/db/migrations
				db.transaction {
					if (oldVersion < 215 && newVersion >= 215) {
						Log.i(TAG, "migrating to version 215")
						execSQL("CREATE TABLE IF NOT EXISTS \"blobs\" (blobId TEXT NOT NULL, content TEXT DEFAULT NULL, dateModified TEXT NOT NULL, utcDateModified TEXT NOT NULL, PRIMARY KEY(blobId))")
						execSQL("ALTER TABLE notes ADD blobId TEXT DEFAULT NULL")
						execSQL("ALTER TABLE note_revisions ADD blobId TEXT DEFAULT NULL")
					}
					if (oldVersion < 216 && newVersion >= 216) {
						Log.i(TAG, "migrating to version 216")
						val existingBlobIds = mutableSetOf<String>()

						rawQuery(
							"SELECT noteId, content, dateModified, utcDateModified FROM note_contents",
							arrayOf()
						).use {
							while (it.moveToNext()) {
								val noteId = it.getString(0)
								val content = it.getBlob(1)
								val dateModified = it.getString(2)
								val utcDateModified = it.getString(3)

								val blobId = Util.contentHash(content)
								if (!existingBlobIds.contains(blobId)) {
									existingBlobIds.add(blobId)
									execSQL(
										"INSERT INTO blobs VALUES (?, ?, ?, ?)",
										arrayOf(blobId, content, dateModified, utcDateModified)
									)
									execSQL(
										"UPDATE entity_changes SET entityName = 'blobs', entityId = ? WHERE entityName = 'note_contents' AND entityId = ?",
										arrayOf(blobId, noteId)
									)
								} else {
									execSQL(
										"DELETE FROM entity_changes WHERE entityName = 'note_contents' AND entityId = ?",
										arrayOf(noteId)
									)
								}
								execSQL(
									"UPDATE notes SET blobId = ? WHERE noteId = ?",
									arrayOf(blobId, noteId)
								)
							}
						}

						rawQuery(
							"SELECT noteRevisionId, content, utcDateModified FROM note_revision_contents",
							arrayOf()
						).use {
							while (it.moveToNext()) {
								val noteRevisionId = it.getString(0)
								val content = it.getBlob(1)
								val utcDateModified = it.getString(2)

								val blobId = Util.contentHash(content)
								if (!existingBlobIds.contains(blobId)) {
									existingBlobIds.add(blobId)
									execSQL(
										"INSERT INTO blobs VALUES (?, ?, ?, ?)",
										arrayOf(blobId, content, utcDateModified, utcDateModified)
									)
									execSQL(
										"UPDATE entity_changes SET entityName = 'blobs', entityId = ? WHERE entityName = 'note_revision_contents' AND entityId = ?",
										arrayOf(blobId, noteRevisionId)
									)
								} else {
									execSQL(
										"DELETE FROM entity_changes WHERE entityName = 'note_revision_contents' AND entityId = ?",
										arrayOf(noteRevisionId)
									)
								}
								execSQL(
									"UPDATE note_revisions SET blobId = ? WHERE noteRevisionId = ?",
									arrayOf(blobId, noteRevisionId)
								)
							}
						}

						// don't bother counting notes without blobs
						// this database migration will never be used by real users anyhow...
					}
					if (oldVersion < 217 && newVersion >= 217) {
						Log.i(TAG, "migrating to version 217")
						execSQL("DROP TABLE note_contents")
						execSQL("DROP TABLE note_revision_contents")
						execSQL("DELETE FROM entity_changes WHERE entityName IN ('note_contents', 'note_revision_contents')")
					}
					if (oldVersion < 218 && newVersion >= 218) {
						Log.i(TAG, "migrating to version 218")
						execSQL(
							"""CREATE TABLE IF NOT EXISTS "revisions" (
							revisionId	TEXT NOT NULL PRIMARY KEY,
                            noteId	TEXT NOT NULL,
                            type TEXT DEFAULT '' NOT NULL,
                            mime TEXT DEFAULT '' NOT NULL,
                            title	TEXT NOT NULL,
                            isProtected	INT NOT NULL DEFAULT 0,
                            blobId TEXT DEFAULT NULL,
                            utcDateLastEdited TEXT NOT NULL,
                            utcDateCreated TEXT NOT NULL,
                            utcDateModified TEXT NOT NULL,
                            dateLastEdited TEXT NOT NULL,
                            dateCreated TEXT NOT NULL)"""
						)
						execSQL(
							"INSERT INTO revisions (revisionId, noteId, type, mime, title, isProtected, utcDateLastEdited, utcDateCreated, utcDateModified, dateLastEdited, dateCreated, blobId) "
									+ "SELECT noteRevisionId, noteId, type, mime, title, isProtected, utcDateLastEdited, utcDateCreated, utcDateModified, dateLastEdited, dateCreated, blobId FROM note_revisions"
						)
						execSQL("DROP TABLE note_revisions")
						execSQL("CREATE INDEX IDX_revisions_noteId ON revisions (noteId)")
						execSQL("CREATE INDEX IDX_revisions_utcDateCreated ON revisions (utcDateCreated)")
						execSQL("CREATE INDEX IDX_revisions_utcDateLastEdited ON revisions (utcDateLastEdited)")
						execSQL("CREATE INDEX IDX_revisions_dateCreated ON revisions (dateCreated)")
						execSQL("CREATE INDEX IDX_revisions_dateLastEdited ON revisions (dateLastEdited)")
						execSQL("UPDATE entity_changes SET entityName = 'revisions' WHERE entityName = 'note_revisions'")
					}
					if (oldVersion < 219 && newVersion >= 219) {
						Log.i(TAG, "migrating to version 219")
						execSQL(
							"""CREATE TABLE IF NOT EXISTS "attachments"(
    						attachmentId      TEXT not null primary key,
    						ownerId       TEXT not null,
    						role         TEXT not null,
    						mime         TEXT not null,
    						title         TEXT not null,
    						isProtected    INT  not null DEFAULT 0,
    						position     INT  default 0 not null,
    						blobId    TEXT DEFAULT null,
    						dateModified TEXT NOT NULL,
    						utcDateModified TEXT not null,
    						utcDateScheduledForErasureSince TEXT DEFAULT NULL,
    						isDeleted    INT  not null,
    						deleteId    TEXT DEFAULT NULL)"""
						)
						execSQL("CREATE INDEX IDX_attachments_ownerId_role ON attachments (ownerId, role)")
						execSQL("CREATE INDEX IDX_attachments_utcDateScheduledForErasureSince ON attachments (utcDateScheduledForErasureSince)")
					}
					if (oldVersion < 220 && newVersion >= 220) {
						Log.i(TAG, "migrating to version 220 (no-op currently)")
						// TODO: auto-convert images
					}
					if (oldVersion < 221 && newVersion >= 221) {
						Log.i(TAG, "migrating to version 221")
						execSQL("DELETE FROM options WHERE name = 'hideIncludedImages_main'")
						execSQL("DELETE FROM entity_changes WHERE entityName = 'options' AND entityId = 'hideIncludedImages_main'")
					}
					if (oldVersion < 222 && newVersion >= 222) {
						Log.i(TAG, "migrating to version 222")
						execSQL("UPDATE options SET name = 'openNoteContexts' WHERE name = 'openTabs'")
						execSQL("UPDATE entity_changes SET entityId = 'openNoteContexts' WHERE entityName = 'options' AND entityId = 'openTabs'")
					}
					// 223 is NOOP
					// 224 is a hotfix, already fixed in 216 above
					if (oldVersion < 225 && newVersion >= 225) {
						Log.i(TAG, "migrating to version 225")
						execSQL("CREATE INDEX IF NOT EXISTS IDX_notes_blobId on notes (blobId)")
						execSQL("CREATE INDEX IF NOT EXISTS IDX_revisions_blobId on revisions (blobId)")
						execSQL("CREATE INDEX IF NOT EXISTS IDX_attachments_blobId on attachments (blobId)")
					}
					if (oldVersion < 226 && newVersion >= 226) {
						Log.i(TAG, "migrating to version 226")
						execSQL("UPDATE attributes SET value = 'contentAndAttachmentsAndRevisionsSize' WHERE name = 'orderBy' AND value = 'noteSize'")
					}
					if (oldVersion < 227 && newVersion >= 227) {
						Log.i(TAG, "migrating to version 227")
						execSQL("UPDATE options SET value = 'false' WHERE name = 'compressImages'")
					}
					if (oldVersion < 228 && newVersion >= 228) {
						Log.i(TAG, "migrating to version 228")
						execSQL("UPDATE blobs SET blobId = REPLACE(blobId, '+', 'A')")
						execSQL("UPDATE blobs SET blobId = REPLACE(blobId, '/', 'B')")
						execSQL("UPDATE notes SET blobId = REPLACE(blobId, '+', 'A')")
						execSQL("UPDATE notes SET blobId = REPLACE(blobId, '/', 'B')")
						execSQL("UPDATE attachments SET blobId = REPLACE(blobId, '+', 'A')")
						execSQL("UPDATE attachments SET blobId = REPLACE(blobId, '/', 'B')")
						execSQL("UPDATE revisions SET blobId = REPLACE(blobId, '+', 'A')")
						execSQL("UPDATE revisions SET blobId = REPLACE(blobId, '/', 'B')")
						execSQL("UPDATE entity_changes SET entityId = REPLACE(entityId, '+', 'A') WHERE entityName = 'blobs'")
						execSQL("UPDATE entity_changes SET entityId = REPLACE(entityId, '/', 'B') WHERE entityName = 'blobs';")
					}
					if (oldVersion < 229 && newVersion >= 229) {
						Log.i(TAG, "migrating to version 229")
						execSQL(
							"CREATE TABLE IF NOT EXISTS user_data (tmpID INT, username TEXT, email TEXT, userIDEncryptedDataKey TEXT," +
									"userIDVerificationHash TEXT,salt TEXT,derivedKey TEXT,isSetup TEXT DEFAULT \"false\"," +
									"UNIQUE (tmpID),PRIMARY KEY (tmpID))"
						)
					}
				}
			} catch (t: Throwable) {
				Log.e(TAG, "fatal error in database migration", t)
			}
		}

		override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
			// TODO: do something here
		}
	}

	// see https://github.com/TriliumNext/Notes/tree/develop/db
	// and https://github.com/TriliumNext/Notes/blob/develop/src/services/app_info.ts
	object Versions {
		const val DATABASE_VERSION_0_59_4 = 213
		const val DATABASE_VERSION_0_60_4 = 214
		const val DATABASE_VERSION_0_61_5 = 225
		const val DATABASE_VERSION_0_62_3 = 227
		const val DATABASE_VERSION_0_63_3 = 228 // same up to 0.92.4
		const val DATABASE_VERSION_0_92_6 = 229

		const val SYNC_VERSION_0_59_4 = 29
		const val SYNC_VERSION_0_60_4 = 29
		const val SYNC_VERSION_0_62_3 = 31
		const val SYNC_VERSION_0_63_3 = 32
		const val SYNC_VERSION_0_90_12 = 33
		const val SYNC_VERSION_0_91_6 = 34 // same up to 0.92.6

		val SUPPORTED_SYNC_VERSIONS: Set<Int> = setOf(
			SYNC_VERSION_0_91_6,
			SYNC_VERSION_0_90_12,
			SYNC_VERSION_0_63_3,
		)
		val SUPPORTED_DATABASE_VERSIONS: Set<Int> = setOf(
			DATABASE_VERSION_0_63_3,
			DATABASE_VERSION_0_92_6,
		)

		const val DATABASE_VERSION = DATABASE_VERSION_0_92_6
		const val DATABASE_NAME = "Document.db"

		// sync version is largely irrelevant
		const val SYNC_VERSION = SYNC_VERSION_0_91_6
		const val APP_VERSION = "0.91.6"
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

fun Boolean.boolToIntValue(): Int = if (this) {
	1
} else {
	0
}

fun Boolean.boolToIntString(): String = if (this) {
	"1"
} else {
	"0"
}

fun String.parseUtcDate(): OffsetDateTime =
	OffsetDateTime.parse(this.replace(' ', 'T'))
