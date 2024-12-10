package eu.fliegendewurst.triliumdroid.data

abstract class Attribute(
	open val name: String,
	var promoted: Boolean,
	val inherited: Boolean,
	val templated: Boolean
) {
	abstract fun value(): String
}