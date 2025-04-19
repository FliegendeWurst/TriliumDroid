package eu.fliegendewurst.triliumdroid.data

import eu.fliegendewurst.triliumdroid.database.Blobs
import eu.fliegendewurst.triliumdroid.service.ProtectedSession
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class Blob(
	val id: BlobId,
	var content: ByteArray,
	var dateModified: String,
	var utcDateModified: String
) {
	@OptIn(ExperimentalEncodingApi::class)
	fun decrypt(): ByteArray? {
		return ProtectedSession.decrypt(Base64.decode(this.content))
	}

	override fun toString(): String = "Blob(${id.id},$dateModified,${content.size} bytes)"
}

data class BlobId(val id: String)

suspend fun BlobId.load(): Blob? = Blobs.load(this)
