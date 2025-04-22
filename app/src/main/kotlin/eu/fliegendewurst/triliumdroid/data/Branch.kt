package eu.fliegendewurst.triliumdroid.data

import eu.fliegendewurst.triliumdroid.database.IdLike

class Branch(
	var id: BranchId,
	val note: NoteId,
	var parentNote: NoteId,
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
		return note.id.compareTo(other.note.id)
	}

	override fun toString(): String {
		return "Branch(${id.id},parent=${parentNote.id},cachedPos=$cachedTreeIndex)"
	}
}

data class BranchId(val id: String) : IdLike {
	override fun rawId() = id
	override fun columnName() = "branchId"
	override fun tableName() = "branches"
}
