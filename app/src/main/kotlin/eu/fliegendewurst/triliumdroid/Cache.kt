package eu.fliegendewurst.triliumdroid

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.AbstractWindowedCursor
import android.database.Cursor
import android.database.CursorWindow
import android.database.sqlite.SQLiteCursorDriver
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteQuery
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.util.Log
import androidx.core.database.getBlobOrNull
import androidx.core.database.sqlite.transaction
import eu.fliegendewurst.triliumdroid.Cache.utcDateModified
import eu.fliegendewurst.triliumdroid.data.Attachment
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.Label
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.Relation
import eu.fliegendewurst.triliumdroid.service.Util
import eu.fliegendewurst.triliumdroid.util.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.decodeBase64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.StrictMath.max
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


@OptIn(ExperimentalEncodingApi::class)
object Cache {
	private const val TAG: String = "Cache"

	@SuppressLint("SimpleDateFormat")
	private val localTime: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ")

	private var notes: MutableMap<String, Note> = ConcurrentHashMap()

	/**
	 * Branches indexed by branch id.
	 */
	private var branches: MutableMap<String, Branch> = ConcurrentHashMap()

	private var branchPosition: MutableMap<String, Int> = ConcurrentHashMap()

	private var dbHelper: CacheDbHelper? = null
	var db: SQLiteDatabase? = null
		private set

	var lastSync: Long? = null

	fun getBranchPosition(id: String): Int? {
		return branchPosition[id]
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
					l.add(getNote(it.getString(0))!!)
				}
				return@withContext l
			}
		}

	suspend fun getNoteWithContent(id: String): Note? {
		Log.d(TAG, "fetching note $id")
		if (notes.containsKey(id) && notes[id]!!.mime != "INVALID" && notes[id]!!.content() != null) {
			return notes[id]
		}
		return getNoteInternal(id)
	}

	suspend fun getAttachmentWithContent(id: String): Attachment? {
		return getAttachmentInternal(id)
	}

	suspend fun setNoteContent(id: String, content: String) = withContext(Dispatchers.IO) {
		if (!notes.containsKey(id)) {
			getNoteInternal(id)
		}
		val data = content.encodeToByteArray()
		notes[id]!!.updateContent(data)

		var blobId = ""
		db!!.rawQuery(
			"SELECT blobId FROM blobs LEFT JOIN notes USING (blobId) WHERE noteId = ?",
			arrayOf(id)
		).use {
			if (it.moveToNext()) {
				blobId = it.getString(0)
			}
		}
		if (blobId == "") {
			Log.e(TAG, "failed to find blob for note")
			return@withContext
		}

		val date = dateModified()
		val utc = utcDateModified()
		db!!.execSQL(
			"UPDATE blobs SET content = ?, dateModified = ?, utcDateModified = ? " +
					"WHERE blobId = ?", arrayOf(notes[id]!!.rawContent()!!, date, utc, blobId)
		)
		val md = MessageDigest.getInstance("SHA-1")
		md.update(data, 0, data.size)
		val sha1hash = md.digest()
		val hash = Base64.encode(sha1hash)
		db!!.execSQL(
			"INSERT OR REPLACE INTO entity_changes (entityName, entityId, hash, isErased, changeId, componentId, instanceId, isSynced, utcDateChanged) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
			arrayOf(
				"blobs",
				blobId,
				hash,
				0,
				Util.randomString(12),
				"NA",
				ConnectionUtil.instanceId,
				1,
				utc
			)
		)
		db!!.execSQL(
			"UPDATE notes SET dateModified = ?, utcDateModified = ? " +
					"WHERE noteId = ?",
			arrayOf(date, utc, id)
		)
		notes[id]!!.modified = date
		db!!.registerEntityChangeNote(notes[id]!!)
	}

	suspend fun updateLabel(note: Note, name: String, value: String, inheritable: Boolean) =
		withContext(Dispatchers.IO) {
			var previousId: String? = null
			// TODO: multi labels
			db!!.rawQuery(
				"SELECT attributeId FROM attributes WHERE noteId = ? AND type = 'label' AND name = ? AND isDeleted = 0",
				arrayOf(note.id, name)
			).use {
				if (it.moveToNext()) {
					previousId = it.getString(0)
				}
			}
			val utc = utcDateModified()
			if (previousId != null) {
				Log.i(TAG, "updating label $note / $name = ${value.length} characters")
				db!!.execSQL(
					"UPDATE attributes SET value = ?, utcDateModified = ? " +
							"WHERE attributeId = ?",
					arrayOf(value, utc, previousId)
				)
			} else {
				var fresh = false
				while (!fresh) {
					previousId = Util.newNoteId()
					// check if it is used
					db!!.rawQuery(
						"SELECT 1 FROM attributes WHERE attributeId = ?",
						arrayOf(previousId)
					)
						.use {
							fresh = !it.moveToNext()
						}
				}
				// TODO: proper position
				db!!.execSQL(
					"INSERT INTO attributes (attributeId, noteId, type, name, value, position, utcDateModified, isDeleted, deleteId, isInheritable) " +
							"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
					arrayOf(
						previousId,
						note.id,
						"label",
						name,
						value,
						10,
						utc,
						0,
						null,
						inheritable
					)
				)
			}
			db!!.registerEntityChangeAttribute(
				previousId!!,
				note.id,
				"label",
				name,
				value,
				inheritable
			)
			note.clearAttributeCache()
			getNoteInternal(note.id)
		}

	suspend fun updateRelation(
		note: Note,
		attributeId: String?,
		name: String,
		value: Note,
		inheritable: Boolean
	) = withContext(Dispatchers.IO) {
		var previousId: String? = attributeId
		val utc = utcDateModified()
		if (previousId != null) {
			db!!.execSQL(
				"UPDATE attributes SET value = ?, utcDateModified = ? " +
						"WHERE attributeId = ?",
				arrayOf(value, utc, previousId)
			)
		} else {
			var fresh = false
			while (!fresh) {
				previousId = Util.newNoteId()
				// check if it is used
				db!!.rawQuery("SELECT 1 FROM attributes WHERE attributeId = ?", arrayOf(previousId))
					.use {
						fresh = !it.moveToNext()
					}
			}
			// TODO: proper position
			db!!.execSQL(
				"INSERT INTO attributes (attributeId, noteId, type, name, value, position, utcDateModified, isDeleted, deleteId, isInheritable) " +
						"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
				arrayOf(
					previousId,
					note.id,
					"relation",
					name,
					value.id,
					10,
					utc,
					0,
					null,
					inheritable
				)
			)
		}
		db!!.registerEntityChangeAttribute(
			previousId!!,
			note.id,
			"relation",
			name,
			value.id,
			inheritable
		)
		note.clearAttributeCache()
		getNoteInternal(note.id)
	}

	suspend fun deleteLabel(note: Note, name: String) = withContext(Dispatchers.IO) {
		Log.d(TAG, "deleting label $name in ${note.id}")
		var previousId: String? = null
		var inheritable = false
		db!!.rawQuery(
			"SELECT attributeId, isInheritable FROM attributes WHERE noteId = ? AND type = 'label' AND name = ? AND isDeleted = 0",
			arrayOf(note.id, name)
		).use {
			if (it.moveToNext()) {
				previousId = it.getString(0)
				inheritable = it.getInt(1) == 1
			}
		}
		if (previousId == null) {
			Log.e(TAG, "failed to find label $name to delete")
			return@withContext
		}
		val utc = utcDateModified()
		db!!.execSQL(
			"UPDATE attributes SET value = '', isDeleted = ?, utcDateModified = ? " +
					"WHERE attributeId = ?",
			arrayOf(1, utc, previousId)
		)
		db!!.registerEntityChangeAttribute(previousId!!, note.id, "label", name, "", inheritable)
		note.clearAttributeCache()
		getNoteInternal(note.id)
	}

	suspend fun deleteRelation(note: Note, name: String, attributeId: String) =
		withContext(Dispatchers.IO) {
			Log.d(TAG, "deleting relation $name in ${note.id}")
			var previousId: String? = null
			var inheritable = false
			db!!.rawQuery(
				"SELECT attributeId, isInheritable FROM attributes WHERE attributeId = ? AND type = 'relation' AND isDeleted = 0",
				arrayOf(attributeId)
			).use {
				if (it.moveToNext()) {
					previousId = it.getString(0)
					inheritable = it.getInt(1) == 1
				}
			}
			if (previousId == null) {
				Log.e(TAG, "failed to find relation $name to delete")
				return@withContext
			}
			val utc = utcDateModified()
			db!!.execSQL(
				"UPDATE attributes SET value = '', isDeleted = ?, utcDateModified = ? " +
						"WHERE attributeId = ?",
				arrayOf(1, utc, previousId)
			)
			db!!.registerEntityChangeAttribute(
				previousId!!,
				note.id,
				"relation",
				name,
				"",
				inheritable
			)
			note.clearAttributeCache()
			getNoteInternal(note.id)
		}

	suspend fun cloneNote(parentBranch: Branch, note: Note) = withContext(Dispatchers.IO) {
		val parentNote = parentBranch.note
		// first, make sure we aren't creating a cycle
		val paths = getNotePaths(parentNote) ?: return@withContext
		if (paths.any { it.any { otherBranch -> otherBranch.note == note.id } }) {
			return@withContext
		}
		// create new branch
		val branchId = "${parentNote}_${note.id}"
		// check if it is used
		db!!.rawQuery("SELECT 1 FROM branches WHERE branchId = ?", arrayOf(branchId))
			.use {
				if (it.moveToNext()) {
					return@withContext
				}
			}
		// TODO: proper position
		val utc = utcDateModified()
		db!!.execSQL(
			"INSERT INTO branches (branchId, noteId, parentNoteId, notePosition, prefix, isExpanded, isDeleted, deleteId, utcDateModified) " +
					"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
			arrayOf(branchId, note.id, parentNote, 0, null, 0, 0, null, utc)
		)
		db!!.registerEntityChangeBranch(Branch(branchId, note.id, parentNote, 0, null, false))
	}

	/**
	 * Get one possible note path for the provided note id.
	 */
	fun getNotePath(id: String): List<Branch> {
		val l = mutableListOf<Branch>()
		var lastId = id
		while (true) {
			db!!.rawQuery(
				"SELECT branchId, parentNoteId, isExpanded, notePosition, prefix FROM branches WHERE noteId = ? AND isDeleted = 0 LIMIT 1",
				arrayOf(lastId)
			).use {
				if (it.moveToNext()) {
					val branchId = it.getString(0)
					val parentId = it.getString(1)
					val expanded = it.getInt(2) == 1
					val notePosition = it.getInt(3)
					val prefix = if (!it.isNull(4)) {
						it.getString(4)
					} else {
						null
					}
					if (branches.containsKey(branchId)) {
						l.add(branches[branchId]!!)
					} else {
						val branch =
							Branch(branchId, lastId, parentId, notePosition, prefix, expanded)
						l.add(branch)
					}
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

	/**
	 * Return all note paths to a given note.
	 */
	suspend fun getNotePaths(noteId: String): List<List<Branch>>? = withContext(Dispatchers.IO) {
		val branches = getNote(noteId)?.branches ?: return@withContext null
		var possibleBranches = branches.map { x -> listOf(x) }.toMutableList()
		while (true) {
			val newPossibleBranches = mutableListOf<List<Branch>>()
			var progress = false
			for (path in possibleBranches) {
				if (path.last().note == "root") {
					newPossibleBranches.add(path)
					continue
				}
				val note = getNote(path.last().parentNote)!!
				for (branch in note.branches) {
					val newPath = path.toMutableList()
					newPath.add(branch)
					newPossibleBranches.add(newPath)
					progress = true
				}
			}
			possibleBranches = newPossibleBranches
			if (!progress) {
				break
			}
		}
		return@withContext possibleBranches
	}

	suspend fun toggleBranch(branch: Branch) = withContext(Dispatchers.IO) {
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
		branches[branch.id]?.expanded = newValue
	}

	suspend fun moveBranch(branch: Branch, newParent: Branch, newPosition: Int) =
		withContext(Dispatchers.IO) {
			Log.i(
				TAG,
				"moving branch ${branch.id} to new parent ${newParent.note}, pos: ${branch.position} -> $newPosition"
			)
			if (branch.parentNote == newParent.note && branch.position == newPosition) {
				return@withContext // no action needed
			}
			val newId = "${newParent.note}_${branch.note}"
			val idChanged = branch.id != newId
			val utc = utcDateModified()
			val args = ContentValues()
			args.put("branchId", newId)
			args.put("noteId", branch.note)
			args.put("parentNoteId", newParent.note)
			args.put("notePosition", newPosition)
			args.put("prefix", branch.prefix)
			args.put("isExpanded", branch.expanded)
			args.put("isDeleted", 0)
			args.putNull("deleteId")
			args.put("utcDateModified", utc)
			if (db!!.insertWithOnConflict("branches", null, args, CONFLICT_REPLACE) == -1L) {
				Log.e(TAG, "error moving branch!")
			}
			if (idChanged) {
				deleteBranch(branch)
				branch.id = newId
			}
			val oldParent = branch.parentNote
			branch.parentNote = newParent.note
			db!!.registerEntityChangeBranch(branch)
			notes[oldParent]?.children = null
			notes[newParent.note]?.children = null
			getTreeData("AND (branches.parentNoteId = \"${oldParent}\" OR branches.parentNoteId = \"${newParent.note}\")")
		}

	suspend fun getNote(id: String): Note? {
		if (notes.containsKey(id) && notes[id]?.mime != "INVALID") {
			return notes[id]
		}
		return getNoteInternal(id)
	}

	suspend fun deleteNote(branch: Branch): Boolean = withContext(Dispatchers.IO) {
		if (branch.note == "root") {
			return@withContext false
		}
		val note = getNote(branch.note) ?: return@withContext false
		Log.i(TAG, "deleting note ${branch.note}")
		// delete child notes if this is the last branch of this note
		if (note.branches.size == 1) {
			for (child in note.children ?: emptySet()) {
				val id2 = child.note
				val note2 = getNote(id2)!!
				if (note2.branches.size == 1) {
					if (!deleteNote(note2.branches[0])) {
						return@withContext false
					}
				}
			}
			notes.remove(branch.note)
			note.id = "DELETED"
			db!!.execSQL("UPDATE notes SET isDeleted=1 WHERE noteId = ?", arrayOf(branch.note))
			db!!.registerEntityChangeNote(note)
		}
		// remove branches
		db!!.execSQL("UPDATE branches SET isDeleted=1 WHERE branchId = ?", arrayOf(branch.id))
		db!!.registerEntityChangeBranch(branch)
		note.branches.remove(branch)
		return@withContext true
	}

	private suspend fun deleteBranch(branch: Branch) = withContext(Dispatchers.IO) {
		Log.i(TAG, "deleting branch ${branch.id}")
		db!!.execSQL("UPDATE branches SET isDeleted=1 WHERE branchId = ?", arrayOf(branch.id))
		db!!.registerEntityChangeBranch(branch)
	}

	suspend fun renameNote(note: Note, title: String) = withContext(Dispatchers.IO) {
		note.updateTitle(title)
		db!!.execSQL(
			"UPDATE notes SET title = ? WHERE noteId = ?",
			arrayOf(note.rawTitle(), note.id)
		)
		db!!.registerEntityChangeNote(note)
	}

	private val FIXER: Regex =
		"\\s*(<div>|<div class=\"[^\"]+\">)(.*)</div>\\s*".toRegex(RegexOption.DOT_MATCHES_ALL)

	private suspend fun getNoteInternal(id: String): Note? = withContext(Dispatchers.IO) {
		var note: Note? = null
		CursorFactory.selectionArgs = arrayOf(id)
		val labels = mutableListOf<Label>()
		val relations = mutableListOf<Relation>()
		db!!.rawQueryWithFactory(
			CursorFactory,
			"SELECT content," + // 0
					"mime," + // 1
					"title," + // 2
					"attributes.type," + // 3
					"attributes.name," + // 4
					"attributes.value," + // 5
					"notes.type," + // 6
					"notes.dateCreated," + // 7
					"notes.dateModified," + // 8
					"attributes.isInheritable," + // 9
					"attributes.isDeleted, " + // 10
					"notes.isProtected, " + // 11
					"notes.blobId, " + // 12
					"attributes.attributeId " + // 13
					"FROM notes LEFT JOIN blobs USING (blobId) " +
					"LEFT JOIN attributes USING(noteId)" +
					"WHERE notes.noteId = ? AND notes.isDeleted = 0 AND (attributes.isDeleted = 0 OR attributes.isDeleted IS NULL)",
			arrayOf(id),
			"notes"
		).use {
			if (it.moveToFirst()) {
				note = Note(
					id,
					it.getString(1),
					it.getString(2),
					it.getString(6),
					it.getString(7),
					it.getString(8),
					it.getInt(11) != 0,
					it.getString(12)
				)
				val noteContent = if (!it.isNull(0)) {
					var content = it.getBlob(0).decodeToString()
					// fixup useless nested divs
					while (true) {
						val contentFixed = FIXER.matchEntire(content)
						if (contentFixed != null) {
							content = contentFixed.groups[2]!!.value
						} else {
							break
						}
					}
					content.toByteArray()
				} else {
					ByteArray(0)
				}
				note!!.updateContentRaw(noteContent)
			}
			while (!it.isAfterLast) {
				if (!it.isNull(3)) {
					val type = it.getString(3)
					val inheritable = it.getInt(9) == 1
					val deleted = it.getInt(10) == 1
					val attributeId = it.getString(13)
					if (!deleted) {
						if (type == "label") {
							val name = it.getString(4)
							val value = it.getString(5)
							labels.add(
								Label(
									name, value, inheritable,
									promoted = false,
									multi = false
								)
							)
						} else if (type == "relation") {
							val name = it.getString(4)
							// value = note ID
							val value = it.getString(5)
							if (!notes.containsKey(value)) {
								notes[value] =
									Note(
										value,
										"INVALID",
										"INVALID",
										"INVALID",
										"INVALID",
										"INVALID",
										false,
										"INVALID"
									)
							}
							relations.add(
								Relation(
									attributeId, notes[value], name, inheritable,
									promoted = false,
									multi = false
								)
							)
						}
					}
				}
				it.moveToNext()
				note!!.setLabels(labels)
				note!!.setRelations(relations)
			}
		}
		if (note != null) {
			val previous = notes[id]
			note!!.branches = previous?.branches ?: mutableListOf()
			note!!.children = previous?.children
			previous?.setLabels(labels)
			previous?.setRelations(relations)
			notes[id] = note!!
		}
		return@withContext note
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
				val blobId = it.getString(12)
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
			}
		}
		val query2 = System.currentTimeMillis() - query2Start
		if (query1 + query2 > 50) {
			Log.w(TAG, "slow getTreeData() $query1 ms + $query2 ms")
		}
	}

	/**
	 * Use [Note.computeChildren] instead
	 */
	suspend fun getChildren(noteId: String) = withContext(Dispatchers.IO) {
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
		if (branchId == "none_root") {
			for ((i, pair) in list.withIndex()) {
				branchPosition[pair.first.note] = i
				pair.first.cachedTreeIndex = i
			}
		}
		return list
	}

	private suspend fun syncPush(): Int = withContext(Dispatchers.IO) {
		var lastSyncedPush = 0
		db!!.rawQuery("SELECT value FROM options WHERE name = ?", arrayOf("lastSyncedPush"))
			.use {
				if (it.moveToFirst()) {
					lastSyncedPush = it.getInt(0)
				}
			}
		Log.i(TAG, "last synced push: $lastSyncedPush")
		val logMarkerId = "trilium-droid"
		val changesUri = "/api/sync/update?logMarkerId=$logMarkerId"

		val data = JSONObject()
		data.put("instanceId", ConnectionUtil.instanceId)
		val entities = JSONArray()
		var largestId = lastSyncedPush
		db!!.rawQuery(
			"SELECT * FROM entity_changes WHERE isSynced = 1 AND id > ?",
			arrayOf(lastSyncedPush.toString())
		).use {

			while (it.moveToNext()) {
				val instanceIdSaved = it.getString(7)
				if (instanceIdSaved != ConnectionUtil.instanceId) {
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
				val instanceId = it.getString(7)
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
		return@withContext entities.length()
	}

	/**
	 * Get row from database table [entityName] with ID [entityId]
	 */
	private suspend fun fetchEntity(entityName: String, entityId: String): JSONObject =
		withContext(Dispatchers.IO) {
			var keyName = entityName.substring(0, entityName.length - 1)
			if (keyName == "note_content") {
				keyName = "note"
			} else if (keyName == "branche") {
				keyName = "branch"
			}
			val obj = JSONObject()
			db!!.rawQuery("SELECT * FROM $entityName WHERE ${keyName}Id = ?", arrayOf(entityId))
				.use {
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
							val column = it.getColumnName(i)
							if (x == null) {
								obj.put(column, null)
								continue
							}
							if (column == "content") {
								val data = when (x) {
									is String -> {
										x.encodeToByteArray()
									}

									is ByteArray -> {
										x
									}

									else -> {
										// impossible / null
										ByteArray(0)
									}
								}
								obj.put(column, Base64.encode(data))
							} else {
								obj.put(column, x)
							}
						}
					}
				}
			return@withContext obj
		}

	suspend fun syncStart(
		callbackOutstanding: (Int) -> Unit,
		callbackError: (Exception) -> Unit,
		callbackDone: (Pair<Int, Int>) -> Unit
	) {
		lastSync = System.currentTimeMillis()
		// first, verify correct sync version
		ConnectionUtil.getAppInfo {
			Log.d(TAG, "app info: $it")
			if (it != null) {
				if (Versions.SUPPORTED_SYNC_VERSIONS.contains(it.syncVersion) && it.dbVersion == Versions.DATABASE_VERSION) {
					runBlocking {
						sync(0, callbackOutstanding, callbackError, callbackDone)
					}
				} else {
					Log.e(TAG, "mismatched sync / database version")
					callbackError(Exception("mismatched sync / database version"))
				}
			} else {
				callbackError(IllegalStateException("did not receive app info"))
			}
		}
	}

	private suspend fun sync(
		alreadySynced: Int,
		callbackOutstanding: (Int) -> Unit,
		callbackError: (Exception) -> Unit,
		callbackDone: (Pair<Int, Int>) -> Unit
	): Unit = withContext(Dispatchers.IO) {
		try {
			val totalPushed = syncPush()
			var totalSynced = alreadySynced
			var lastSyncedPull = 0
			db!!.rawQuery("SELECT value FROM options WHERE name = ?", arrayOf("lastSyncedPull"))
				.use {
					if (it.moveToFirst()) {
						lastSyncedPull = it.getInt(0)
					}
				}
			val logMarkerId = "trilium-droid"
			val changesUri =
				"/api/sync/changed?instanceId=${ConnectionUtil.instanceId}&lastEntityChangeId=${lastSyncedPull}&logMarkerId=${logMarkerId}"

			ConnectionUtil.doSyncRequest(changesUri, { resp ->
				val outstandingPullCount = resp.getInt("outstandingPullCount")
				val entityChangeId = resp.getInt("lastEntityChangeId")
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
							// already applied
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

						if (entityName == "notes") {
							notes[entityName]?.makeInvalid()
							notes.remove(entityName)
						}

						val cv = ContentValues(entity.length())
						keys.map { fieldName ->
							var x = entity.get(fieldName)
							if (x == JSONObject.NULL) {
								cv.putNull(fieldName)
							} else {
								if (fieldName == "content") {
									x = (x as String).decodeBase64()!!.toByteArray()
								}
								when (x) {
									is String -> {
										cv.put(fieldName, x)
									}

									is Int -> {
										cv.put(fieldName, x)
									}

									is Double -> {
										cv.put(fieldName, x)
									}

									is ByteArray -> {
										cv.put(fieldName, x)
									}

									else -> {
										Log.e(
											TAG,
											"failed to recognize sync entity value $x of type ${x.javaClass}"
										)
									}
								}
							}
						}
						db!!.insertWithOnConflict(entityName, null, cv, CONFLICT_REPLACE)
					}
				}
				Log.i(TAG, "last entity change id: $entityChangeId")
				val utc = utcDateModified()
				db!!.execSQL(
					"INSERT OR REPLACE INTO options (name, value, utcDateModified) VALUES (?, ?, ?)",
					arrayOf("lastSyncedPull", entityChangeId, utc)
				)
				if (outstandingPullCount > 0) {
					callbackOutstanding(outstandingPullCount)
					runBlocking {
						sync(totalSynced, callbackOutstanding, callbackError, callbackDone)
					}
				} else {
					callbackDone(Pair(totalSynced, totalPushed))
				}
			}, callbackError)
		} catch (e: Exception) {
			Log.e(TAG, "sync error", e)
			callbackError(e)
		}
	}

	fun haveDatabase(context: Context): Boolean {
		val file = File(context.filesDir.parent, "databases/Document.db")
		return file.exists()
	}

	suspend fun initializeDatabase(context: Context) = withContext(Dispatchers.IO) {
		if (db != null && db!!.isOpen) {
			return@withContext
		}
		try {
			val sql = context.resources.openRawResource(R.raw.schema).bufferedReader()
				.use { it.readText() }
			dbHelper = CacheDbHelper(context, sql)
			db = dbHelper!!.writableDatabase
		} catch (t: Throwable) {
			Log.e(TAG, "fatal ", t)
		}
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
		branchPosition.clear()
		lastSync = null
	}

	fun closeDatabase() {
		db?.close()
		dbHelper?.close()
	}

	suspend fun createChildNote(parentNote: Note, newNoteTitle: String?): Note =
		withContext(Dispatchers.IO) {
			// create entries in notes, blobs, branches
			var newId = Util.newNoteId()
			val newBlobId = Util.newNoteId()
			db!!.transaction {
				do {
					var exists = true
					rawQuery("SELECT noteId FROM notes WHERE noteId = ?", arrayOf(newId)).use {
						if (!it.moveToNext()) {
							exists = false
						}
					}
					if (!exists) {
						break
					}
					newId = Util.newNoteId()
				} while (true)
				val branchId = "${parentNote.id}_$newId"
				val notePosition = 0 // TODO: sorting
				val prefix = null
				val isExpanded = 0
				val isDeleted = 0
				val deleteId = null
				val dateModified = dateModified()
				val utcDateModified = utcDateModified()
				execSQL(
					"INSERT INTO branches VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
					arrayOf(
						branchId,
						newId,
						parentNote.id,
						notePosition,
						prefix,
						isExpanded,
						isDeleted,
						deleteId,
						utcDateModified
					)
				)
				// hash "branchId", "noteId", "parentNoteId", "prefix"
				registerEntityChange(
					"branches",
					branchId,
					arrayOf(branchId, newId, parentNote.id, "null")
				)
				execSQL(
					"INSERT INTO blobs VALUES (?, ?, ?, ?)",
					arrayOf(
						newBlobId,
						"",
						dateModified,
						utcDateModified
					)
				)
				registerEntityChange("blobs", newBlobId, arrayOf(newBlobId, ""))
				execSQL(
					"INSERT INTO notes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
					arrayOf(
						newId,
						newNoteTitle,
						"0", // isProtected
						"text", // type
						"text/html", // mime
						newBlobId, // blobId
						"0", // isDeleted
						null, // deleteId
						dateModified, // dateCreated
						dateModified,
						utcDateModified, // utcDateCreated
						utcDateModified,
					)
				)
				// hash ["noteId", "title", "isProtected", "type", "mime", "blobId"]
				registerEntityChange(
					"notes",
					newId,
					arrayOf(newId, newNoteTitle ?: "", "0", "text", "text/html", newBlobId)
				)
			}
			getTreeData("AND noteId = \"$newId\"")
			return@withContext getNote(newId)!!
		}

	suspend fun createSiblingNote(siblingNote: Note, newNoteTitle: String?): Note =
		withContext(Dispatchers.IO) {
			val parentNote = getNotePath(siblingNote.id)
			if (parentNote.size == 1) {
				// root note can't have siblings
				return@withContext createChildNote(siblingNote, newNoteTitle)
			}
			return@withContext createChildNote(getNote(parentNote[1].note)!!, newNoteTitle)
		}

	suspend fun addInternalLink(note: Note, target: String) = withContext(Dispatchers.IO) {
		val relations = note.getRelations()
		if (relations.any { x -> x.target?.id == target && x.name == "internalLink" }) {
			return@withContext
		}
		updateRelation(note, null, "internalLink", getNote(target) ?: return@withContext, false)
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
					"LEFT JOIN attributes USING(noteId) " +
					"WHERE notes.isDeleted = 0 " +
					"AND attributes.isDeleted = 0 " +
					"AND attributes.type == 'relation' " +
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
					currentNote = Note(id, "", title, "", "", "", false, "")
				}
				if (attrValue != null && !attrValue.startsWith('_') && !attrName.startsWith(
						"child:"
					)
				) {
					relations.add(Triple(id, attrValue, Pair(attrName, attrId)))
				}
			}
			list.add(currentNote!!)
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

	private fun dateModified(): String {
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
					Log.i(TAG, "successfully created ${it.count} tables")
				}
			} catch (t: Throwable) {
				Log.e(TAG, "fatal error creating database", t)
			}
		}

		override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
			try {
				// source: https://github.com/zadam/trilium/tree/master/db/migrations
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
		const val DATABASE_VERSION_0_63_3 = 228 // same up to 0.91.6
		const val SYNC_VERSION_0_59_4 = 29
		const val SYNC_VERSION_0_60_4 = 29
		const val SYNC_VERSION_0_62_3 = 31
		const val SYNC_VERSION_0_63_3 = 32
		const val SYNC_VERSION_0_90_12 = 33
		const val SYNC_VERSION_0_91_6 = 34

		val SUPPORTED_SYNC_VERSIONS: Set<Int> = setOf(
			SYNC_VERSION_0_91_6,
			SYNC_VERSION_0_90_12,
			SYNC_VERSION_0_63_3,
		)

		const val DATABASE_VERSION = DATABASE_VERSION_0_63_3
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

private suspend fun SQLiteDatabase.registerEntityChangeNote(
	note: Note,
) {
	// hash ["noteId", "title", "isProtected", "type", "mime", "blobId"]
	registerEntityChange(
		"notes",
		note.id,
		arrayOf(
			note.id,
			note.rawTitle(),
			note.isProtected.boolToInt(),
			note.type,
			note.mime,
			note.blobId
		)
	)
}

private suspend fun SQLiteDatabase.registerEntityChangeAttribute(
	attributeId: String,
	noteId: String,
	type: String,
	name: String,
	value: String,
	isInheritable: Boolean
) {
	// hash ["attributeId", "noteId", "type", "name", "value", "isInheritable"]
	registerEntityChange(
		"attributes",
		attributeId,
		arrayOf(
			attributeId, noteId, type, name, value, if (isInheritable) {
				"1"
			} else {
				"0"
			}
		)
	)
}

private suspend fun SQLiteDatabase.registerEntityChangeBranch(
	branch: Branch,
) {
	// hash ["branchId", "noteId", "parentNoteId", "prefix"]
	registerEntityChange(
		"branches",
		branch.id,
		arrayOf(branch.id, branch.note, branch.parentNote, branch.prefix ?: "null")
	)
}

@OptIn(ExperimentalEncodingApi::class)
private suspend fun SQLiteDatabase.registerEntityChange(
	table: String,
	id: String,
	toHash: Array<String>,
) = withContext(Dispatchers.IO) {
	val utc = utcDateModified()
	val md = MessageDigest.getInstance("SHA-1")
	for (h in toHash) {
		val x = "|${h}".encodeToByteArray()
		md.update(x, 0, x.size)
	}
	val sha1hash = md.digest()
	val hash = Base64.encode(sha1hash)
	execSQL(
		"INSERT OR REPLACE INTO entity_changes (entityName, entityId, hash, isErased, changeId, componentId, instanceId, isSynced, utcDateChanged) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
		arrayOf(
			table,
			id,
			hash,
			0,
			Util.randomString(12),
			"NA",
			ConnectionUtil.instanceId,
			1,
			utc
		)
	)
}

private fun Boolean.boolToInt(): String = if (this) {
	"1"
} else {
	"0"
}
