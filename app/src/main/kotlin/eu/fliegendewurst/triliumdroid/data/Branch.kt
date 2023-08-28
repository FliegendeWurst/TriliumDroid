package eu.fliegendewurst.triliumdroid.data

import java.util.SortedMap

data class Branch(
	val id: String,
	val note: String,
	val parentNote: String?,
	val position: Int,
	val prefix: String?,
	var expanded: Boolean,
	public var children: SortedMap<Int, Branch>
)
