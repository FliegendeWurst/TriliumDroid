package eu.fliegendewurst.triliumdroid.data

import eu.fliegendewurst.triliumdroid.database.Blobs
import eu.fliegendewurst.triliumdroid.database.IdLike

class NoteRevision(
	val note: Note,
	val revisionId: RevisionId,
	val type: String,
	val mime: String,
	val title: String,
	val isProtected: Boolean,
	val blobId: BlobId,
	val utcDateLastEdited: String,
	/**
	 * When this revision was created.
	 * Mostly useless as it does not state anything about the time the revision was modified.
	 */
	val utcDateCreated: String,
	val utcDateModified: String,
	val dateLastEdited: String,
	val dateCreated: String
) {
	private var content: Blob? = null

	suspend fun content(): Blob {
		if (content != null) {
			return content!!
		} else {
			content = Blobs.load(blobId)!!
			return content!!
		}
	}
}

data class RevisionId(val id: String) : IdLike {
	override fun rawId() = id
	override fun columnName() = "revisionId"
	override fun tableName() = "revisions"
}
