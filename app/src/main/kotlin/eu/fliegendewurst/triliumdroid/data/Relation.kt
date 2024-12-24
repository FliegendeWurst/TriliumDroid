package eu.fliegendewurst.triliumdroid.data

class Relation(
	val id: String?,
	val target: Note?,
	name: String,
	val inheritable: Boolean,
	promoted: Boolean,
	val multi: Boolean,
	inherited: Boolean = false,
	templated: Boolean = false
) : Attribute(name, promoted, inherited, templated) {
	override fun value(): String {
		return target?.id!!
	}

	fun makeInherited(): Relation {
		return Relation(id, target, name, inheritable, promoted, multi, true)
	}

	fun makeTemplated(): Relation {
		return Relation(id, target, name, inheritable, promoted, multi, true)
	}
}
