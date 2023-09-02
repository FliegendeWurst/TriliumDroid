package eu.fliegendewurst.triliumdroid.data

import eu.fliegendewurst.triliumdroid.*
import java.util.*

class Note(
	var id: String,
	val mime: String,
	var title: String,
	var type: String,
	val created: String,
	var modified: String
) {
	var content: ByteArray? = null
	var contentFixed: Boolean = false
	private var labels: List<Label>? = null
	private var relations: List<Relation>? = null
	private var inheritedLabels: List<Label>? = null
	private var inheritedRelations: List<Relation>? = null
	var children: SortedMap<Int, Branch>? = null
	var branches: MutableList<Branch> = mutableListOf()
	private var inheritableCached = false

	fun getLabel(name: String): String? {
		return getLabelValue(name)?.value
	}

	fun getLabelValue(name: String): Label? {
		if (!inheritableCached) {
			cacheInheritableAttributes()
		}
		for (label in labels.orEmpty() + inheritedLabels.orEmpty()) {
			if (label.name == name) {
				return label
			}
		}
		return null
	}

	fun getRelation(name: String): Note? {
		return getRelationValue(name)?.target
	}

	fun getRelationValue(name: String): Relation? {
		if (!inheritableCached) {
			cacheInheritableAttributes()
		}
		for (relation in relations.orEmpty() + inheritedRelations.orEmpty()) {
			if (relation.name == name) {
				return relation
			}
		}
		return null
	}

	private fun cacheInheritableAttributes() {
		if (id == "none" || inheritableCached) {
			return
		}
		val paths = Cache.getNotePaths(id) ?: return
		val allLabels = mutableListOf<Label>()
		val allRelations = mutableListOf<Relation>()
		for (path in paths) {
			val parent = path[0].parentNote
			// inheritable attrs on root are typically not intended to be applied to hidden subtree #3537
			if (parent == "none" || id == "root" || id == "_hidden") {
				continue
			}
			val parentNote = Cache.getNoteWithContent(parent) ?: continue
			parentNote.cacheInheritableAttributes()
			for (label in parentNote.labels.orEmpty() + parentNote.inheritedLabels.orEmpty()) {
				if (label.inheritable) {
					allLabels.add(label.makeInherited())
				}
			}
			for (relation in parentNote.relations.orEmpty() + parentNote.inheritedRelations.orEmpty()) {
				if (relation.inheritable) {
					allRelations.add(relation.makeInherited())
				}
			}
		}
		// TODO: make sure this filtering logic makes sense
		val filteredLabels = mutableListOf<Label>()
		for (x in allLabels.filter { !labels.orEmpty().any { label -> label.name == it.name } }) {
			if (filteredLabels.any { it.name == x.name }) {
				continue
			}
			filteredLabels.add(x)
		}
		inheritedLabels = filteredLabels
		val filteredRelations = mutableListOf<Relation>()
		for (x in allRelations.filter {
			!relations.orEmpty().any { relation -> relation.name == it.name }
		}) {
			if (filteredRelations.any { it.name == x.name }) {
				continue
			}
			filteredRelations.add(x)
		}
		inheritedRelations = filteredRelations
		inheritableCached = true
		// handle templates
		var template = getRelation("template")
		if (template != null) {
			template = Cache.getNoteWithContent(template.id)!!
			// add attributes
			template.cacheInheritableAttributes()
			for (label in template.labels.orEmpty()) {
				if (getLabelValue(label.name) == null) {
					labels = labels.orEmpty() + label.makeTemplated()
				}
			}
			for (label in template.inheritedLabels.orEmpty()) {
				if (getLabelValue(label.name) == null) {
					filteredLabels.add(label.makeTemplated())
					inheritedLabels = filteredLabels // not sure this is needed
				}
			}
			for (relation in template.relations.orEmpty()) {
				if (getRelationValue(relation.name) == null) {
					relations = relations.orEmpty() + relation.makeTemplated()
				}
			}
			for (relation in template.inheritedRelations.orEmpty()) {
				if (getRelationValue(relation.name) == null) {
					filteredRelations.add(relation.makeTemplated())
					inheritedRelations = filteredRelations // not sure this is needed
				}
			}
		}
	}

	fun getAttributes(): List<Attribute> {
		if (!inheritableCached) {
			cacheInheritableAttributes()
		}
		return labels.orEmpty() + relations.orEmpty() + inheritedLabels.orEmpty() + inheritedRelations.orEmpty()
	}

	fun setLabels(labels: List<Label>) {
		this.labels = labels
	}

	fun setRelations(relations: List<Relation>) {
		this.relations = relations
	}
}