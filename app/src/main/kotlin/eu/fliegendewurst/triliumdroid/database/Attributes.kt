package eu.fliegendewurst.triliumdroid.database

import android.util.Log
import eu.fliegendewurst.triliumdroid.data.AttributeId
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.NoteId
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
			var previousId: AttributeId? = null
			// TODO: multi labels
			DB.rawQuery(
				"SELECT attributeId FROM attributes WHERE noteId = ? AND type = 'label' AND name = ? AND isDeleted = 0",
				arrayOf(note.id.rawId(), name)
			).use {
				if (it.moveToNext()) {
					previousId = AttributeId(it.getString(0))
				}
			}
			val utc = utcDateModified()
			if (previousId != null) {
				Log.i(TAG, "updating label $note / $name = ${value.length} characters")
				DB.update(previousId!!, Pair("value", value), Pair("utcDateModified", utc))
			} else {
				previousId = AttributeId(DB.newId("attributes", "attributeId") { Util.newNoteId() })
				// TODO: proper position
				DB.insert(
					"attributes",
					Pair("attributeId", previousId),
					Pair("noteId", note.id),
					Pair("type", "label"),
					Pair("name", name),
					Pair("value", value),
					Pair("position", 10),
					Pair("utcDateModified", utc),
					Pair("isDeleted", false),
					Pair("deleteId", null),
					Pair("isInheritable", false)
				)
			}
			registerEntityChangeAttribute(
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
		attributeId: AttributeId?,
		name: String,
		value: Note,
		inheritable: Boolean
	) = withContext(Dispatchers.IO) {
		var previousId: AttributeId? = attributeId
		val utc = utcDateModified()
		if (previousId != null) {
			DB.update(previousId, Pair("value", value), Pair("utcDateModified", utc))
		} else {
			previousId = AttributeId(DB.newId("attributes", "attributeId") { Util.newNoteId() })
			// TODO: proper position
			DB.insert(
				"attributes",
				Pair("attributeId", previousId),
				Pair("noteId", note.id),
				Pair("type", "relation"),
				Pair("name", name),
				Pair("value", value.id),
				Pair("position", 10),
				Pair("utcDateModified", utc),
				Pair("isDeleted", false),
				Pair("deleteId", null),
				Pair("isInheritable", inheritable)
			)
		}
		registerEntityChangeAttribute(
			previousId!!,
			note.id,
			"relation",
			name,
			value.id.rawId(),
			inheritable
		)
		note.clearAttributeCache()
		note.makeInvalid()
		Notes.getNoteWithContent(note.id)
	}

	suspend fun deleteLabel(note: Note, name: String) = withContext(Dispatchers.IO) {
		Log.d(TAG, "deleting label $name in ${note.id.rawId()}")
		var previousId: AttributeId? = null
		var inheritable = false
		DB.rawQuery(
			"SELECT attributeId, isInheritable FROM attributes WHERE noteId = ? AND type = 'label' AND name = ? AND isDeleted = 0",
			arrayOf(note.id.rawId(), name)
		).use {
			if (it.moveToNext()) {
				previousId = AttributeId(it.getString(0))
				inheritable = it.getInt(1) == 1
			}
		}
		if (previousId == null) {
			Log.e(TAG, "failed to find label $name to delete")
			return@withContext
		}
		val utc = utcDateModified()
		DB.update(
			previousId!!,
			Pair("value", ""),
			Pair("isDeleted", true),
			Pair("deleteId", Util.newDeleteId()),
			Pair("utcDateModified", utc)
		)
		registerEntityChangeAttribute(previousId!!, note.id, "label", name, "", inheritable)
		note.clearAttributeCache()
		note.makeInvalid()
		Notes.getNoteWithContent(note.id)
	}

	suspend fun deleteRelation(note: Note, name: String, attributeId: AttributeId) =
		withContext(Dispatchers.IO) {
			Log.d(TAG, "deleting relation $name in ${note.id}")
			var previousId: AttributeId? = null
			var inheritable = false
			DB.rawQuery(
				"SELECT attributeId, isInheritable FROM attributes WHERE attributeId = ? AND type = 'relation' AND isDeleted = 0",
				arrayOf(attributeId.rawId())
			).use {
				if (it.moveToNext()) {
					previousId = AttributeId(it.getString(0))
					inheritable = it.getInt(1) == 1
				}
			}
			if (previousId == null) {
				Log.e(TAG, "failed to find relation $name to delete")
				return@withContext
			}
			val utc = utcDateModified()
			DB.update(
				previousId!!,
				Pair("value", ""),
				Pair("isDeleted", true),
				Pair("deleteId", Util.newDeleteId()),
				Pair("utcDateModified", utc)
			)
			registerEntityChangeAttribute(
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

private suspend fun registerEntityChangeAttribute(
	attributeId: AttributeId,
	noteId: NoteId,
	type: String,
	name: String,
	value: String,
	isInheritable: Boolean
) {
	// hash ["attributeId", "noteId", "type", "name", "value", "isInheritable"]
	// source: https://github.com/TriliumNext/Notes/blob/develop/src/becca/entities/battribute.ts
	Cache.registerEntityChange(
		"attributes",
		attributeId.rawId(),
		arrayOf(
			attributeId.rawId(), noteId.rawId(), type, name, value, if (isInheritable) {
				"1"
			} else {
				"0"
			}
		)
	)
}
