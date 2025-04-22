package eu.fliegendewurst.triliumdroid.util

class Unreachable : Error() {
	override val message: String?
		get() = "unreachable code entered / database connection broken"
}
