package eu.fliegendewurst.triliumdroid.data

import java.util.*

class Note(var id: String, val mime: String, var title: String, var type: String, val created: String, var modified: String) {
	var content: ByteArray? = null
	var contentFixed: Boolean = false
	var labels: List<Label>? = null
	var relations: List<Relation>? = null
	var children: SortedMap<Int, Branch>? = null
	var branches: MutableList<Branch> = mutableListOf()

	fun getLabel(name: String): String? {
		for (label in labels.orEmpty()) {
			if (label.name == name) {
				return label.value
			}
		}
		return null
	}
}