package eu.fliegendewurst.triliumdroid.data

class Blob(
	val blobId: String,
	val content: ByteArray,
	val dateModified: String,
	val utcDateModified: String
) {
	override fun toString(): String = "Blob($blobId,$dateModified,${content.size} bytes)"
}
