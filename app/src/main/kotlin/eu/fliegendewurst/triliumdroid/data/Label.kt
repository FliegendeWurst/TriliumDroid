package eu.fliegendewurst.triliumdroid.data

class Label(
	name: String,
	val value: String,
	val inheritable: Boolean,
	promoted: Boolean,
	val multi: Boolean,
	inherited: Boolean = false,
) : Attribute(name, promoted, inherited) {
	override fun value(): String {
		return value
	}

	fun makeInherited(): Label {
		return Label(name, value, inheritable, promoted, multi, true)
	}

	fun makeTemplated(): Label {
		return Label(name, value, inheritable, promoted, multi, true)
	}

}