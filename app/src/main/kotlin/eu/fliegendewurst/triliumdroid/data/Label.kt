package eu.fliegendewurst.triliumdroid.data

class Label(
	name: String,
	val value: String,
	val inheritable: Boolean,
	promoted: Boolean,
	val multi: Boolean,
	inherited: Boolean = false,
	templated: Boolean = false,
) : Attribute(name, promoted, inherited, templated) {
	override fun value(): String {
		return value
	}

	fun makeInherited(): Label {
		return Label(name, value, inheritable, promoted, multi, inherited = true, templated = false)
	}

	fun makeTemplated(): Label {
		return Label(name, value, inheritable, promoted, multi, inherited = false, templated = true)
	}

}