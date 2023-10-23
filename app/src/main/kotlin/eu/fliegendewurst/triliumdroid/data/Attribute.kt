package eu.fliegendewurst.triliumdroid.data

abstract class Attribute(open val name: String, val promoted: Boolean, val inherited: Boolean) {
	abstract fun value(): String
}