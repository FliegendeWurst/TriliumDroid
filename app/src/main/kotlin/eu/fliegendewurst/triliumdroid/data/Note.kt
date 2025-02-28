package eu.fliegendewurst.triliumdroid.data

import android.util.Log
import eu.fliegendewurst.triliumdroid.database.Branches
import eu.fliegendewurst.triliumdroid.database.Cache
import eu.fliegendewurst.triliumdroid.database.NoteRevisions
import eu.fliegendewurst.triliumdroid.database.Notes
import eu.fliegendewurst.triliumdroid.service.ProtectedSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.SortedSet
import java.util.TreeSet
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class Note(
	var id: String,
	val mime: String,
	private var title: String,
	var type: String,
	val created: String,
	var modified: String,
	var isProtected: Boolean,
	var blobId: String
) : Comparable<Note>, ProtectedSession.NotifyProtectedSessionEnd {
	companion object {
		private const val TAG = "Note"
	}

	private var content: ByteArray? = null
	var contentFixed: Boolean = false
		private set
	private var contentDecrypted: ByteArray? = null
	private var titleDecrypted: String? = null
	private var labels: List<Label>? = null
	private var relations: List<Relation>? = null
	private var inheritedLabels: List<Label>? = null
	private var inheritedRelations: List<Relation>? = null
	var children: SortedSet<Branch>? = null
	var icon: String? = null
	private var revisions: MutableList<NoteRevision>? = null

	/**
	 * Note clones for this note.
	 */
	var branches: MutableList<Branch> = mutableListOf()
	private var inheritableCached = false

	/**
	 * Relations with target = this. Only available when constructing global notes map.
	 */
	var incomingRelations: List<Relation>? = null

	fun icon(): String {
		return icon ?: "bx bx-file-blank"
	}

	suspend fun getLabel(name: String): String? {
		return getLabelValue(name)?.value
	}

	suspend fun getLabelValue(name: String): Label? {
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

	suspend fun getRelation(name: String): Note? {
		return getRelationValue(name)?.target
	}

	suspend fun getRelationValue(name: String): Relation? {
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

	suspend fun computeChildren(): SortedSet<Branch> = withContext(Dispatchers.IO) {
		if (children != null) {
			return@withContext children!!
		}
		Cache.getChildren(id)
		return@withContext children ?: TreeSet()
	}

	private suspend fun cacheInheritableAttributes(): Unit = withContext(Dispatchers.IO) {
		if (id == "none" || inheritableCached) {
			return@withContext
		}
		val paths = Branches.getNotePaths(id) ?: return@withContext
		val allLabels = mutableListOf<Label>()
		val allRelations = mutableListOf<Relation>()
		for (path in paths) {
			val parent = path[0].parentNote
			// inheritable attrs on root are typically not intended to be applied to hidden subtree #3537
			if (parent == "none" || id == "root" || id == "_hidden") {
				continue
			}
			val parentNote = Notes.getNoteWithContent(parent) ?: continue
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
			template = Notes.getNoteWithContent(template.id)!!
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
		// check for (inherited) label:ABC attributes
		for (label in getLabels()) {
			if (label.name.startsWith("label:")) {
				val data = label.value
				val target = label.name.removePrefix("label:")
				val targetLabel = getLabelValue(target) ?: continue
				// TODO: handle e.g. "single" or "text"
				targetLabel.promoted = targetLabel.promoted || data.contains("promoted")
			}
		}
		Log.d(
			TAG,
			"inheritable cache: labels ${inheritedLabels?.size}, relations ${inheritedRelations?.size}"
		)
	}

	fun clearAttributeCache() {
		makeInvalid()
	}

	suspend fun getAttributes(): List<Attribute> {
		if (!inheritableCached) {
			cacheInheritableAttributes()
		}
		return labels.orEmpty() + relations.orEmpty() + inheritedLabels.orEmpty() + inheritedRelations.orEmpty()
	}

	suspend fun getLabels(): List<Label> {
		if (!inheritableCached) {
			cacheInheritableAttributes()
		}
		return labels.orEmpty() + inheritedLabels.orEmpty()
	}

	fun setLabels(labels: List<Label>) {
		this.labels = labels
	}

	suspend fun getRelations(): List<Relation> {
		if (!inheritableCached) {
			cacheInheritableAttributes()
		}
		return relations.orEmpty() + inheritedRelations.orEmpty()
	}

	fun getRelationsBypassCache(): List<Relation> {
		return relations.orEmpty() + inheritedRelations.orEmpty()
	}

	fun setRelations(relations: List<Relation>) {
		this.relations = relations
	}

	fun makeInvalid() {
		this.titleDecrypted = null
		updateTitle("INVALID")
		this.content = null
		this.contentDecrypted = null
		inheritableCached = false
		labels = emptyList()
		inheritedLabels = emptyList()
		relations = emptyList()
		inheritedRelations = emptyList()
		revisions = mutableListOf()
	}

	fun invalid(): Boolean = title == "INVALID" || mime == "INVALID"

	@OptIn(ExperimentalEncodingApi::class)
	fun updateTitle(newTitle: String) {
		if (isProtected && !ProtectedSession.isActive()) {
			Log.e(TAG, "cannot rename protected note")
			return
		} else if (isProtected && ProtectedSession.isActive()) {
			this.titleDecrypted = newTitle
			this.title =
				Base64.encode(ProtectedSession.encrypt(titleDecrypted!!.encodeToByteArray())!!)
		} else {
			this.title = newTitle
		}
	}

	@OptIn(ExperimentalEncodingApi::class)
	fun title() = if (isProtected && !ProtectedSession.isActive()) {
		"[protected]"
	} else if (isProtected && ProtectedSession.isActive()) {
		titleDecrypted = ProtectedSession.decrypt(Base64.decode(this.title))!!.decodeToString()
		ProtectedSession.addListener(this)
		titleDecrypted!!
	} else {
		title
	}

	fun rawTitle() = title

	@OptIn(ExperimentalEncodingApi::class)
	fun content() = if (content == null) {
		null
	} else if (isProtected && !ProtectedSession.isActive()) {
		"[protected]".encodeToByteArray()
	} else if (isProtected && ProtectedSession.isActive()) {
		if (contentDecrypted != null) {
			contentDecrypted
		} else {
			contentDecrypted = ProtectedSession.decrypt(Base64.decode(content!!))
			ProtectedSession.addListener(this)
			contentDecrypted
		}
	} else {
		content
	}

	/**
	 * Get note content encoded for database.
	 * If [isProtected], encrypted and Base64-encoded.
	 */
	fun rawContent() = content

	fun fixContent(fixed: ByteArray) {
		if (isProtected) {
			this.contentDecrypted = fixed
		} else {
			this.content = fixed
		}
		this.contentFixed = true
	}

	/**
	 * Set user-facing note content.
	 */
	@OptIn(ExperimentalEncodingApi::class)
	fun updateContent(new: ByteArray) {
		if (isProtected && !ProtectedSession.isActive()) {
			Log.e(TAG, "tried to update protected note without session")
			return
		}
		if (isProtected && ProtectedSession.isActive()) {
			this.contentDecrypted = new
			this.content =
				Base64.encode(ProtectedSession.encrypt(contentDecrypted!!)!!).encodeToByteArray()
		} else {
			this.content = new
		}
	}

	fun updateContentRaw(new: ByteArray) {
		this.content = new
		this.contentDecrypted = null
	}

	suspend fun changeProtection(protected: Boolean) {
		if (!ProtectedSession.isActive() || this.content == null) {
			return
		}
		if (isProtected && !protected) {
			val content = content() ?: return
			val title = title()
			isProtected = false
			Notes.renameNote(this, title)
			Notes.setNoteContent(id, content.decodeToString())
		} else if (!isProtected && protected) {
			isProtected = true
			Notes.renameNote(this, title)
			Notes.setNoteContent(id, this.content!!.decodeToString())
		}
	}

	suspend fun revisions(): List<NoteRevision> {
		if (revisions != null) {
			return revisions!!
		}
		revisions = NoteRevisions.list(this).toMutableList()
		return revisions!!
	}

	override fun compareTo(other: Note): Int {
		return id.compareTo(other.id)
	}

	override fun sessionExpired() {
		contentDecrypted = null
	}

	override fun toString(): String {
		return "Note($id)"
	}
}
