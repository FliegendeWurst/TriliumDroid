package eu.fliegendewurst.triliumdroid.data

import java.util.*

class Note(var id: String, val mime: String, var title: String, var type: String) {
	var content: ByteArray? = null
	var contentFixed: Boolean = false
	var labels: List<Label>? = null
	var relations: List<Relation>? = null
	var children: SortedMap<Int, Branch>? = null
	var branches: MutableList<Branch> = mutableListOf()
}