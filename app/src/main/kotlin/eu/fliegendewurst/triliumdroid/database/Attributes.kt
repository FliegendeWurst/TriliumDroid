package eu.fliegendewurst.triliumdroid.database

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.database.Cache.db
import eu.fliegendewurst.triliumdroid.database.Cache.utcDateModified
import eu.fliegendewurst.triliumdroid.service.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Attributes {
	private const val TAG = "Attributes"

	suspend fun setPromoted(note: Note, name: String, promoted: Boolean) {
		val previousValue = note.getLabel("label:${name}")
		val newValue: String
		if (promoted) {
			newValue = if (previousValue.isNullOrBlank()) {
				"promoted"
			} else if (previousValue.contains("promoted")) {
				previousValue
			} else {
				"$previousValue,promoted"
			}
		} else {
			newValue = if (previousValue.isNullOrBlank()) {
				""
			} else if (previousValue.contains("promoted")) {
				previousValue.replace("promoted", "").replace(",,", ",")
			} else {
				previousValue
			}
		}
		updateLabel(note, "label:$name", newValue, false)
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
			note.makeInvalid()
			Notes.getNoteWithContent(note.id)
		}

	/**
	 * Update or create a relation.
	 *
	 * @param attributeId the attribute ID, null if creating a new relation
	 */
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
		note.makeInvalid()
		Notes.getNoteWithContent(note.id)
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
		note.makeInvalid()
		Notes.getNoteWithContent(note.id)
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
			note.makeInvalid()
			Notes.getNoteWithContent(note.id)
		}
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
	// source: https://github.com/TriliumNext/Notes/blob/develop/src/becca/entities/battribute.ts
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
