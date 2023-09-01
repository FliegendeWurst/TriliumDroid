package eu.fliegendewurst.triliumdroid.data

data class Label(
	override val name: String,
	val value: String,
	val inheritable: Boolean,
	val inherited: Boolean = false
) : Attribute(name) {
	override fun value(): String {
		return value
	}

	fun makeInherited(): Label {
		return Label(name, value, inheritable, true)
	}

	fun makeTemplated(): Label {
		return Label(name, value, inheritable, inherited)
	}

}