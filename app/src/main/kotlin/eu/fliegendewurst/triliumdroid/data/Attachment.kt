package eu.fliegendewurst.triliumdroid.data

import eu.fliegendewurst.triliumdroid.database.Blobs
import eu.fliegendewurst.triliumdroid.database.IdLike

class Attachment(
	var id: AttachmentId,
	val ownerId: NoteId,
	val role: AttachmentRole,
	val mime: String,
	val title: String,
	val protected: Boolean,
	val position: Int,
	val blobId: BlobId?,
	val dateModified: String,
	val utcDateModified: String,
	val utcDateScheduledForErasureSince: String?,
	val deleted: Boolean,
	val deleteId: String?
) {
	suspend fun blob(): Blob? {
		val blobId = this.blobId
		if (blobId != null) {
			return Blobs.load(blobId)
		}
		return null
	}
}

data class AttachmentId(val id: String) : IdLike {
	override fun rawId() = id
	override fun columnName() = "attachmentId"
	override fun tableName() = "attachments"
	override fun toString() = id
}

enum class AttachmentRole {
	File,
	Image,

	/**
	 * Raw value: 'viewConfig', used for Geo Map configuration.
	 * Example value: `{"view":{"center":{"lat":3.6782592009044848,"lng":55.1953125},"zoom":2}}`
	 */
	ViewConfig
}

object AttachmentNames {
	/**
	 * Reference: [Trilium source](https://github.com/TriliumNext/Trilium/blob/v0.98.1/apps/server/src/migrations/0233__migrate_geo_map_to_collection.ts#L27)
	 */
	const val GEOMAP_VIEWCONFIG = "geoMap.json"
}

fun String.asAttachmentRole(): AttachmentRole? =
	when (this) {
		"file" -> AttachmentRole.File
		"image" -> AttachmentRole.Image
		"viewConfig" -> AttachmentRole.ViewConfig
		else -> null
	}

fun AttachmentRole.asString(): String =
	when (this) {
		AttachmentRole.File -> "file"
		AttachmentRole.Image -> "image"
		AttachmentRole.ViewConfig -> "viewConfig"
	}
