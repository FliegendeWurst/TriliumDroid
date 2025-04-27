package eu.fliegendewurst.triliumdroid.database

import android.content.ContentValues
import android.util.Log
import androidx.core.database.getBlobOrNull
import androidx.core.database.getStringOrNull
import eu.fliegendewurst.triliumdroid.data.AttributeId
import eu.fliegendewurst.triliumdroid.data.Blob
import eu.fliegendewurst.triliumdroid.data.BlobId
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.Label
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.NoteId
import eu.fliegendewurst.triliumdroid.data.Relation
import eu.fliegendewurst.triliumdroid.database.Cache.dateModified
import eu.fliegendewurst.triliumdroid.database.Cache.utcDateModified
import eu.fliegendewurst.triliumdroid.database.DB.CursorFactory
import eu.fliegendewurst.triliumdroid.service.ProtectedSession
import eu.fliegendewurst.triliumdroid.service.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object Notes {
	private const val TAG = "Notes"

	/**
	 * Unique root note of the note tree.
	 */
	val ROOT = NoteId("root")

	/**
	 * "Parent note" of the root note. Does not exist in the database!
	 * When used for [Branch.parentNote], indicates a note without parents.
	 */
	val NONE = NoteId("none")

	/**
	 * Root note of the Trilium-provided hidden notes.
	 */
	val HIDDEN = NoteId("_hidden")

	/**
	 * Root note of the user-provided hidden notes.
	 */
	val USER_HIDDEN = NoteId("_userHidden")

	val notes: MutableMap<NoteId, Note> = ConcurrentHashMap()

	suspend fun createChildNote(parentNote: Note, newNoteTitle: String?): Note {
		// create entries in notes, blobs, branches
		var newId = NoteId(DB.newId("notes", "noteId ") { Util.newNoteId() })
		val dateModified = dateModified()
		val utcDateModified = utcDateModified()

		val blob = Blobs.new(null)
		DB.insert(
			"notes",
			Pair("noteId", newId),
			Pair("title", newNoteTitle),
			Pair("isProtected", false),
			Pair("type", "text"),
			Pair("mime", "text/html"),
			Pair("isDeleted", false),
			Pair("deleteId", null),
			Pair("dateCreated", dateModified),
			Pair("dateModified", dateModified),
			Pair("utcDateCreated", utcDateModified),
			Pair("utcDateModified", utcDateModified),
			Pair("blobId", blob.id)
		)
		// hash ["noteId", "title", "isProtected", "type", "mime", "blobId"]
		registerEntityChangeNote(
			Note(
				newId,
				"text/html",
				newNoteTitle ?: "",
				"text",
				false,
				null,
				dateModified,
				dateModified,
				utcDateModified,
				utcDateModified,
				false,
				blob.id
			)
		)
		Branches.cloneNote(parentNote.id, newId)
		Tree.getTreeData("AND noteId = '$newId'")
		return getNote(newId)!!
	}

	suspend fun createSiblingNote(siblingNote: Note, newNoteTitle: String?): Note =
		withContext(Dispatchers.IO) {
			val parentNote = Branches.getNotePath(siblingNote.id)
			if (parentNote.size == 1) {
				// root note can't have siblings
				return@withContext createChildNote(siblingNote, newNoteTitle)
			}
			return@withContext createChildNote(getNote(parentNote[1].note)!!, newNoteTitle)
		}

	suspend fun getRootNote(): Note {
		val root = getNote(ROOT)
		if (root == null) {
			Log.e(TAG, "fatal: root note not found")
			throw IllegalStateException("fatal: root note not found")
		}
		return root
	}

	suspend fun getNotesByType(type: String): List<NoteId> = withContext(Dispatchers.IO) {
		val l = mutableListOf<NoteId>()
		DB.rawQuery("SELECT noteId FROM notes WHERE type = ?", arrayOf(type)).use {
			while (it.moveToNext()) {
				l.add(NoteId(it.getString(0)))
			}
		}
		return@withContext l
	}

	suspend fun getNote(id: NoteId): Note? {
		val note = notes[id]
		if (note != null && !note.invalid()) {
			return note
		}
		return getNoteInternal(id)
	}

	suspend fun getNoteWithContent(id: NoteId): Note? {
		val note = notes[id]
		if (note != null && !note.invalid() && note.content() != null) {
			return note
		}
		return getNoteInternal(id)
	}


	private suspend fun getNoteInternal(id: NoteId): Note? = withContext(Dispatchers.IO) {
		Log.d(TAG, "fetching note $id")
		var note: Note? = null
		CursorFactory.selectionArgs = arrayOf(id.id)
		val labels = mutableListOf<Label>()
		val relations = mutableListOf<Relation>()
		DB.rawQueryWithFactory(
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
					"attributes.attributeId, " + // 13
					"blobs.dateModified, " + // 14
					"blobs.utcDateModified, " + // 15
					"notes.utcDateCreated, " + // 16
					"notes.utcDateModified, " + // 17
					"notes.isDeleted, " + // 18
					"notes.deleteId " + // 19
					"FROM notes LEFT JOIN blobs USING (blobId) " +
					"LEFT JOIN attributes USING(noteId)" +
					"WHERE notes.noteId = ? AND (attributes.isDeleted = 0 OR attributes.isDeleted IS NULL)",
			arrayOf(id.id),
			"notes"
		).use {
			if (it.moveToFirst()) {
				val blobId = BlobId(it.getString(12))
				val blobDateModified = it.getString(14)
				val blobUtcDateModified = it.getString(15)
				val utcDateCreated = it.getString(16)
				val utcDateModified = it.getString(17)
				val isDeleted = it.getInt(18).intValueToBool()
				val deleteId = it.getStringOrNull(19)
				note = Note(
					id,
					it.getString(1),
					it.getString(2),
					it.getString(6),
					isDeleted,
					deleteId,
					it.getString(7),
					it.getString(8),
					utcDateCreated,
					utcDateModified,
					it.getInt(11) != 0,
					blobId
				)
				val noteContent = it.getBlobOrNull(0)
				if (noteContent != null) {
					val blob = Blob(blobId, noteContent, blobDateModified, blobUtcDateModified)
					Blobs.loadInternal(blob)
					note!!.updateContentRaw(blob)
				}
			}
			while (!it.isAfterLast) {
				if (!it.isNull(3)) {
					val type = it.getString(3)
					val inheritable = it.getInt(9) == 1
					val deleted = it.getInt(10) == 1
					val attributeId = AttributeId(it.getString(13))
					if (!deleted) {
						if (type == "label") {
							val name = it.getString(4)
							val value = it.getString(5)
							labels.add(
								Label(
									attributeId,
									name, value, inheritable,
									promoted = false,
									multi = false
								)
							)
						} else if (type == "relation") {
							val name = it.getString(4)
							// value = note ID
							val value = NoteId(it.getString(5))
							if (!notes.containsKey(value)) {
								notes[value] = invalidNote(value)
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

	fun invalidNote(id: NoteId, blobId: BlobId = BlobId("INVALID")) = Note(
		id,
		"INVALID",
		"INVALID",
		"INVALID",
		true,
		"INVALID",
		"INVALID",
		"INVALID",
		"INVALID",
		"INVALID",
		false,
		blobId
	)

	suspend fun setNoteContent(id: NoteId, content: String) = withContext(Dispatchers.IO) {
		if (!notes.containsKey(id)) {
			getNoteInternal(id)
		}
		val data = content.encodeToByteArray()
		if (!notes[id]!!.updateContent(data)) {
			return@withContext
		}

		val date = dateModified()
		val utc = utcDateModified()
		DB.update(
			id,
			Pair("dateModified", date),
			Pair("utcDateModified", utc),
			Pair("isProtected", notes[id]!!.isProtected.boolToIntValue()),
			Pair("blobId", notes[id]!!.blobId.id)
		)
		notes[id]!!.modified = date
		notes[id]!!.utcModified = utc
		registerEntityChangeNote(notes[id]!!)
	}

	suspend fun setBlobId(id: NoteId, blobId: BlobId) = withContext(Dispatchers.IO) {
		val cv = ContentValues()
		cv.put("blobId", blobId.id)
		DB.update(id, Pair("blobId", blobId))
		registerEntityChangeNote(getNote(id)!!)
	}

	/**
	 * Update the note's date modified, and store all to the database.
	 */
	suspend fun refreshDatabaseRow(note: Note) = withContext(Dispatchers.IO) {
		val date = dateModified()
		val utc = utcDateModified()
		val cv = ContentValues()
		cv.put("dateModified", date)
		cv.put("utcDateModified", utc)
		cv.put("isProtected", note.isProtected.boolToIntValue())
		cv.put("blobId", note.blobId.id)
		DB.update(
			note.id,
			Pair("dateModified", date),
			Pair("utcDateModified", utc),
			Pair("isProtected", note.isProtected),
			Pair("blobId", note.blobId)
		)
		note.modified = date
		note.utcModified = utc
		registerEntityChangeNote(note)
	}

	suspend fun changeNoteProtection(id: NoteId, protection: Boolean) {
		if (!ProtectedSession.isActive()) {
			return
		}
		if (!notes.containsKey(id)) {
			getNoteInternal(id)
		}
		val note = notes[id] ?: return
		note.changeProtection(protection)
	}

	suspend fun renameNote(note: Note, title: String) = withContext(Dispatchers.IO) {
		note.updateTitle(title)
		DB.update(note.id, Pair("title", note.rawTitle()))
		registerEntityChangeNote(note)
	}

	suspend fun addInternalLink(note: Note, target: NoteId) {
		val relations = note.getRelations()
		if (relations.any { x -> x.target?.id == target && x.name == "internalLink" }) {
			return
		}
		Attributes.updateRelation(
			note,
			null,
			"internalLink",
			getNote(target) ?: return,
			false
		)
	}

	suspend fun deleteNote(branch: Branch): Boolean = withContext(Dispatchers.IO) {
		if (branch.note == ROOT) {
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
			note.id = NoteId("DELETED")
			// TODO: deleteId?
			DB.update(branch.note, Pair("isDeleted", true), Pair("deleteId", Util.randomString(10)))
			registerEntityChangeNote(note)
		}
		// remove branches
		Branches.delete(branch)
		note.branches.remove(branch)
		// clean up stale reference in parent
		val parentNote = notes[branch.parentNote]
		if (parentNote != null) {
			parentNote.children = null
		}
		return@withContext true
	}
}

private suspend fun registerEntityChangeNote(note: Note) {
	// hash ["noteId", "title", "isProtected", "type", "mime", "blobId"]
	Cache.registerEntityChange(
		"notes",
		note.id.id,
		arrayOf(
			note.id.id,
			note.rawTitle(),
			note.isProtected.boolToIntString(),
			note.type,
			note.mime,
			note.blobId.id
		)
	)
}
