package eu.fliegendewurst.triliumdroid.data

class Note(var id: String, val mime: String, var title: String, var type: String) {
	var content: ByteArray? = null
	var contentFixed: Boolean = false
	var labels: List<Label>? = null
	var relations: List<Relation>? = null
}