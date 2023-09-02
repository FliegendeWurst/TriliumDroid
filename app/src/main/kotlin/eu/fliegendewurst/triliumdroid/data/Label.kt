package eu.fliegendewurst.triliumdroid.data

class Label(
	override val name: String,
	val value: String,
	val inheritable: Boolean,
	promoted: Boolean,
	val multi: Boolean,
	val inherited: Boolean = false,
) : Attribute(name, promoted) {
	override fun value(): String {
		return value
	}

	fun makeInherited(): Label {
		return Label(name, value, inheritable, promoted, multi, true)
	}

	fun makeTemplated(): Label {
		return Label(name, value, inheritable, promoted, multi, inherited)
	}

}