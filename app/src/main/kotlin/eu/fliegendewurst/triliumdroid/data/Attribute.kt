package eu.fliegendewurst.triliumdroid.data

abstract class Attribute(open val name: String, val promoted: Boolean) {
	abstract fun value(): String
}