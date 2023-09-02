package eu.fliegendewurst.triliumdroid.data

class Relation(
	val target: Note?,
	override val name: String,
	val inheritable: Boolean,
	promoted: Boolean,
	val multi: Boolean,
	val inherited: Boolean = false
) : Attribute(name, promoted) {
	override fun value(): String {
		return target?.id!!
	}

	fun makeInherited(): Relation {
		return Relation(target, name, inheritable, promoted, multi, true)
	}

	fun makeTemplated(): Relation {
		return Relation(target, name, inheritable, promoted, multi, inherited)
	}
}
