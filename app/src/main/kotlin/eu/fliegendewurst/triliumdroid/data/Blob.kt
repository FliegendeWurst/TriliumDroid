package eu.fliegendewurst.triliumdroid.data

class Blob(
	val blobId: String,
	var content: ByteArray,
	var dateModified: String,
	var utcDateModified: String
) {
	override fun toString(): String = "Blob($blobId,$dateModified,${content.size} bytes)"
}
