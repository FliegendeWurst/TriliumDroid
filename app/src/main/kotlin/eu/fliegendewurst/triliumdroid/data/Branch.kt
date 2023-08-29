package eu.fliegendewurst.triliumdroid.data

data class Branch(
	val id: String,
	val note: String,
	val parentNote: String?,
	val position: Int,
	val prefix: String?,
	var expanded: Boolean
)
