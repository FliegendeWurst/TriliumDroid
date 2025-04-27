package eu.fliegendewurst.triliumdroid.data

import eu.fliegendewurst.triliumdroid.database.IdLike

abstract class Attribute(
	val id: AttributeId,
	open val name: String,
	var promoted: Boolean,
	val inherited: Boolean,
	val templated: Boolean
) {
	abstract fun value(): String
}

data class AttributeId(val id: String) : IdLike {
	override fun rawId() = id
	override fun columnName() = "attributeId"
	override fun tableName() = "attributes"
	override fun toString() = id
}
