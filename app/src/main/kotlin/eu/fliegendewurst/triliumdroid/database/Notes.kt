package eu.fliegendewurst.triliumdroid.database

import android.content.ContentValues
import android.util.Log
import androidx.core.database.getBlobOrNull
import eu.fliegendewurst.triliumdroid.data.Blob
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.Label
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.Relation
import eu.fliegendewurst.triliumdroid.database.Cache.CursorFactory
import eu.fliegendewurst.triliumdroid.database.Cache.dateModified
import eu.fliegendewurst.triliumdroid.database.Cache.db
import eu.fliegendewurst.triliumdroid.database.Cache.getTreeData
import eu.fliegendewurst.triliumdroid.database.Cache.utcDateModified
import eu.fliegendewurst.triliumdroid.service.ProtectedSession
import eu.fliegendewurst.triliumdroid.service.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object Notes {
	private const val TAG = "Notes"

	val notes: MutableMap<String, Note> = ConcurrentHashMap()

	suspend fun createChildNote(parentNote: Note, newNoteTitle: String?): Note =
		withContext(Dispatchers.IO) {
			// create entries in notes, blobs, branches
			var newId = Util.newNoteId()
			do {
				var exists = true
				db!!.rawQuery("SELECT noteId FROM notes WHERE noteId = ?", arrayOf(newId)).use {
					if (!it.moveToNext()) {
						exists = false
					}
				}
				if (!exists) {
					break
				}
				newId = Util.newNoteId()
			} while (true)
			val dateModified = dateModified()
			val utcDateModified = utcDateModified()

			val blob = Blobs.new(null)
			db!!.execSQL(
				"INSERT INTO notes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
				arrayOf(
					newId,
					newNoteTitle,
					"0", // isProtected
					"text", // type
					"text/html", // mime
					blob.blobId, // blobId
					"0", // isDeleted
					null, // deleteId
					dateModified, // dateCreated
					dateModified,
					utcDateModified, // utcDateCreated
					utcDateModified,
				)
			)
			// hash ["noteId", "title", "isProtected", "type", "mime", "blobId"]
			registerEntityChangeNote(
				Note(
					newId,
					"text/html",
					newNoteTitle ?: "",
					"text",
					dateModified,
					dateModified,
					utcDateModified,
					utcDateModified,
					false,
					blob.blobId
				)
			)
			Branches.cloneNote(parentNote.id, newId)
			getTreeData("AND noteId = '$newId'")
			return@withContext getNote(newId)!!
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

	suspend fun getNote(id: String): Note? {
		val note = notes[id]
		if (note != null && !note.invalid()) {
			return note
		}
		return getNoteInternal(id)
	}

	suspend fun getNoteWithContent(id: String): Note? {
		Log.d(TAG, "fetching note $id")
		val note = notes[id]
		if (note != null && !note.invalid() && note.content() != null) {
			return note
		}
		return getNoteInternal(id)
	}


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
					"attributes.attributeId, " + // 13
					"blobs.dateModified, " + // 14
					"blobs.utcDateModified, " + // 15
					"notes.utcDateCreated, " + // 16
					"notes.utcDateModified " + // 17
					"FROM notes LEFT JOIN blobs USING (blobId) " +
					"LEFT JOIN attributes USING(noteId)" +
					"WHERE notes.noteId = ? AND notes.isDeleted = 0 AND (attributes.isDeleted = 0 OR attributes.isDeleted IS NULL)",
			arrayOf(id),
			"notes"
		).use {
			if (it.moveToFirst()) {
				val blobId = it.getString(12)
				val blobDateModified = it.getString(14)
				val blobUtcDateModified = it.getString(15)
				val utcDateCreated = it.getString(16)
				val utcDateModified = it.getString(17)
				note = Note(
					id,
					it.getString(1),
					it.getString(2),
					it.getString(6),
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
					val attributeId = it.getString(13)
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


	suspend fun setNoteContent(id: String, content: String) = withContext(Dispatchers.IO) {
		if (!notes.containsKey(id)) {
			getNoteInternal(id)
		}
		val data = content.encodeToByteArray()
		notes[id]!!.updateContent(data)

		val date = dateModified()
		val utc = utcDateModified()
		val cv = ContentValues()
		cv.put("dateModified", date)
		cv.put("utcDateModified", utc)
		cv.put("isProtected", notes[id]!!.isProtected.boolToIntValue())
		cv.put("blobId", notes[id]!!.blobId)
		db!!.update("notes", cv, "noteId = ?", arrayOf(id))
		notes[id]!!.modified = date
		notes[id]!!.utcModified = utc
		registerEntityChangeNote(notes[id]!!)
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
		cv.put("blobId", note.blobId)
		db!!.update("notes", cv, "noteId = ?", arrayOf(note.id))
		note.modified = date
		note.utcModified = utc
		registerEntityChangeNote(note)
	}

	suspend fun changeNoteProtection(id: String, protection: Boolean) {
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
		db!!.execSQL(
			"UPDATE notes SET title = ? WHERE noteId = ?",
			arrayOf(note.rawTitle(), note.id)
		)
		registerEntityChangeNote(note)
	}

	suspend fun addInternalLink(note: Note, target: String) {
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
			registerEntityChangeNote(note)
		}
		// remove branches
		Branches.delete(branch)
		note.branches.remove(branch)
		return@withContext true
	}
}

private suspend fun registerEntityChangeNote(note: Note) {
	// hash ["noteId", "title", "isProtected", "type", "mime", "blobId"]
	Cache.registerEntityChange(
		"notes",
		note.id,
		arrayOf(
			note.id,
			note.rawTitle(),
			note.isProtected.boolToIntString(),
			note.type,
			note.mime,
			note.blobId
		)
	)
}
