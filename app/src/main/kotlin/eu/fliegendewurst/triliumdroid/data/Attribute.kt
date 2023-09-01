package eu.fliegendewurst.triliumdroid.data

abstract class Attribute(open val name: String) {
	abstract fun value(): String
}