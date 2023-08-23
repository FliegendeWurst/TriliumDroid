package eu.fliegendewurst.triliumdroid.data

class Relation(val source: Note, val target: Note?, name: String) : Attribute(name) {
	override fun value(): String {
		return target?.id!!
	}
}
