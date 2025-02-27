package eu.fliegendewurst.triliumdroid.data

import eu.fliegendewurst.triliumdroid.database.Blobs

class NoteRevision(
	val note: Note,
	val revisionId: String,
	val type: String,
	val mime: String,
	val title: String,
	val isProtected: Boolean,
	private val blobId: String,
	val utcDateLastEdited: String,
	val utcDateCreated: String,
	val utcDateModified: String,
	val dateLastEdited: String,
	val dateCreated: String
) {
	private var content: Blob? = null

	fun content(): Blob {
		if (content != null) {
			return content!!
		} else {
			content = Blobs.load(blobId)!!
			return content!!
		}
	}
}
