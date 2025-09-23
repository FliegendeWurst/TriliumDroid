package eu.fliegendewurst.triliumdroid.database

import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.NoteId

object HiddenNotes {
	val GEO_MAP_TEMPLATE: Note = run {
		val x = Note(
			NoteId("_template_geo_map"),
			"",
			"Geo Map",
			"book",
			false,
			null,
			Cache.dateModified(),
			Cache.dateModified(),
			Cache.utcDateModified(),
			Cache.utcDateModified(),
			false,
			Blobs.EMPTY_BLOB_ID
		)
		x.updateContentRaw(Blobs.EMPTY_BLOB)
		return@run x
	}
}
