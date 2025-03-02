package eu.fliegendewurst.triliumdroid.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.database.getStringOrNull
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.NoteRevision
import eu.fliegendewurst.triliumdroid.database.Cache.db
import eu.fliegendewurst.triliumdroid.service.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NoteRevisions {
	private const val TAG = "NoteRevisions"

	suspend fun create(note: Note): Unit = withContext(Dispatchers.IO) {
		val cv = ContentValues()
		val revisionId = Util.newNoteId()
		cv.put("revisionId", revisionId)
		cv.put("noteId", note.id)
		cv.put("type", note.type)
		cv.put("mime", note.mime)
		cv.put("title", note.rawTitle())
		cv.put("isProtected", note.isProtected.boolToIntValue())
		cv.put("blobId", note.blobId)
		cv.put("utcDateLastEdited", note.utcModified)
		// FIXME: this should be the date of previous revision
		// if that makes sense..
		cv.put("utcDateCreated", note.utcModified)
		cv.put("utcDateModified", note.utcModified)
		cv.put("dateLastEdited", note.modified)
		cv.put("dateCreated", note.modified)
		val r = NoteRevision(
			note,
			revisionId,
			note.type,
			note.mime,
			note.rawTitle(),
			note.isProtected,
			note.blobId,
			note.utcModified,
			note.utcModified,
			note.utcModified,
			note.modified,
			note.modified
		)
		if (db!!.insertWithOnConflict("revisions", null, cv, SQLiteDatabase.CONFLICT_FAIL) == -1L) {
			create(note)
			return@withContext
		}
		registerEntityChangeNoteRevision(r)
		note.revisionsInvalidate()
	}

	/**
	 * Prefer to use [Note.revisions], which will cache the result.
	 *
	 * @return previous revisions of the provided note
	 */
	suspend fun list(note: Note): List<NoteRevision> = withContext(Dispatchers.IO) {
		val revs = mutableListOf<NoteRevision>()
		db!!.query(
			"revisions",
			arrayOf(
				"revisionId",
				"type",
				"mime",
				"title",
				"isProtected",
				"blobId",
				"utcDateLastEdited",
				"utcDateCreated",
				"utcDateModified",
				"dateLastEdited",
				"dateCreated"
			),
			"noteId = ?",
			arrayOf(note.id),
			null, null, null
		).use {
			while (it.moveToNext()) {
				val revisionId = it.getString(0)
				val type = it.getString(1)
				val mime = it.getString(2)
				val title = it.getString(3)
				val isProtected = it.getInt(4) != 0
				val blobId = it.getStringOrNull(5)
				val utcDateLastEdited = it.getString(6)
				val utcDateCreated = it.getString(7)
				val utcDateModified = it.getString(8)
				val dateLastEdited = it.getString(9)
				val dateCreated = it.getString(10)

				if (blobId == null) {
					// Technically the field is nullable, but I've never had this happen.
					Log.w(TAG, "ignoring revision $revisionId without blobId")
					continue
				}

				revs.add(
					NoteRevision(
						note,
						revisionId,
						type,
						mime,
						title,
						isProtected,
						blobId,
						utcDateLastEdited,
						utcDateCreated,
						utcDateModified,
						dateLastEdited,
						dateCreated
					)
				)
			}
		}
		return@withContext revs
	}

	suspend fun delete(noteRevision: NoteRevision) = withContext(Dispatchers.IO) {
		Blobs.delete(noteRevision.blobId)
		db!!.delete("revisions", "revisionId = ?", arrayOf(noteRevision.revisionId))
		registerEntityChangeNoteRevision(noteRevision, true)
		noteRevision.note.makeInvalid()
	}
}

private suspend fun registerEntityChangeNoteRevision(r: NoteRevision, deleted: Boolean = false) {
	// hash ["revisionId", "noteId", "title", "isProtected", "dateLastEdited", "dateCreated",
	// "utcDateLastEdited", "utcDateCreated", "utcDateModified", "blobId"]
	// source: https://github.com/TriliumNext/Notes/blob/develop/src/becca/entities/brevision.ts
	Cache.registerEntityChange(
		"revisions",
		r.revisionId,
		arrayOf(
			r.revisionId,
			r.note.id,
			r.title,
			r.isProtected.boolToIntString(),
			r.dateLastEdited,
			r.dateCreated,
			r.utcDateLastEdited,
			r.utcDateCreated,
			r.utcDateModified,
			r.blobId
		),
		deleted
	)
}
