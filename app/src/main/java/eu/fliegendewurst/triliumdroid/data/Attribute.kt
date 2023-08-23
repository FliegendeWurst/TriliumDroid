package eu.fliegendewurst.triliumdroid.data

abstract class Attribute(val name: String) {
	abstract fun value(): String
}