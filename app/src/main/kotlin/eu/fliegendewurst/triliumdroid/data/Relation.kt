package eu.fliegendewurst.triliumdroid.data

class Relation(
	val target: Note?,
	name: String,
	val inheritable: Boolean,
	promoted: Boolean,
	val multi: Boolean,
	inherited: Boolean = false
) : Attribute(name, promoted, inherited) {
	override fun value(): String {
		return target?.id!!
	}

	fun makeInherited(): Relation {
		return Relation(target, name, inheritable, promoted, multi, true)
	}

	fun makeTemplated(): Relation {
		return Relation(target, name, inheritable, promoted, multi, true)
	}
}
