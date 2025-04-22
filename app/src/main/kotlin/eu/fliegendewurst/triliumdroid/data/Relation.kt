package eu.fliegendewurst.triliumdroid.data

class Relation(
	id: AttributeId,
	val target: Note?,
	name: String,
	val inheritable: Boolean,
	promoted: Boolean,
	val multi: Boolean,
	inherited: Boolean = false,
	templated: Boolean = false
) : Attribute(id, name, promoted, inherited, templated) {
	override fun value(): String {
		return target?.id!!.id
	}

	fun makeInherited(): Relation {
		return Relation(id, target, name, inheritable, promoted, multi, true)
	}

	fun makeTemplated(): Relation {
		return Relation(id, target, name, inheritable, promoted, multi, true)
	}
}
