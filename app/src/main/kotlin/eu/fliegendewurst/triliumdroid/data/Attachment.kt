package eu.fliegendewurst.triliumdroid.data

class Attachment(
	var id: String,
	val mime: String
) {
	var content: ByteArray? = null
}