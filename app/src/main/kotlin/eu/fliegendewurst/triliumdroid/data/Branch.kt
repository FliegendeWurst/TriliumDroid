package eu.fliegendewurst.triliumdroid.data

class Branch(
	var id: String,
	val note: String,
	var parentNote: String,
	val position: Int,
	val prefix: String?,
	var expanded: Boolean,
	var cachedTreeIndex: Int? = null
) : Comparable<Branch> {
	override fun compareTo(other: Branch): Int {
		val x = position.compareTo(other.position)
		if (x != 0) {
			return x
		}
		return note.compareTo(other.note)
	}
}
