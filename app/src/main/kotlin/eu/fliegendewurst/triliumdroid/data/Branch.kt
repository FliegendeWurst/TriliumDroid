package eu.fliegendewurst.triliumdroid.data

data class Branch(
	var id: String,
	val note: String,
	var parentNote: String,
	val position: Int,
	val prefix: String?,
	var expanded: Boolean,
	var cachedTreeIndex: Int? = null
)
