package eu.fliegendewurst.triliumdroid.data

data class Relation(
	val target: Note?,
	override val name: String,
	val inheritable: Boolean,
	val inherited: Boolean = false
) : Attribute(name) {
	override fun value(): String {
		return target?.id!!
	}

	fun makeInherited(): Relation {
		return Relation(target, name, inheritable, true)
	}

	fun makeTemplated(): Relation {
		return Relation(target, name, inheritable, inherited)
	}
}
