package eu.fliegendewurst.triliumdroid.data

class Label(
	id: String,
	name: String,
	val value: String,
	val inheritable: Boolean,
	promoted: Boolean,
	val multi: Boolean,
	inherited: Boolean = false,
	templated: Boolean = false,
) : Attribute(id, name, promoted, inherited, templated) {
	override fun value(): String {
		return value
	}

	fun makeInherited(): Label {
		// TODO: check if this ID still makes sense?
		return Label(
			id,
			name,
			value,
			inheritable,
			promoted,
			multi,
			inherited = true,
			templated = false
		)
	}

	fun makeTemplated(): Label {
		// TODO: check if this ID still makes sense?
		return Label(
			id,
			name,
			value,
			inheritable,
			promoted,
			multi,
			inherited = false,
			templated = true
		)
	}

}
