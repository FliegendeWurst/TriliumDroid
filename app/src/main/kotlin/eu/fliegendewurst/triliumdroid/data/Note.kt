package eu.fliegendewurst.triliumdroid.data

import android.util.Log
import eu.fliegendewurst.triliumdroid.database.Blobs
import eu.fliegendewurst.triliumdroid.database.Branches
import eu.fliegendewurst.triliumdroid.database.Cache
import eu.fliegendewurst.triliumdroid.database.HiddenNotes
import eu.fliegendewurst.triliumdroid.database.IdLike
import eu.fliegendewurst.triliumdroid.database.NoteRevisions
import eu.fliegendewurst.triliumdroid.database.Notes
import eu.fliegendewurst.triliumdroid.database.parseUtcDate
import eu.fliegendewurst.triliumdroid.service.Option
import eu.fliegendewurst.triliumdroid.service.ProtectedSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.InvalidCipherTextException
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class Note(
	var id: NoteId,
	val mime: String,
	private var title: String,
	var type: String,
	val deleted: Boolean,
	val deleteId: String?,
	val created: String,
	var modified: String,
	var utcCreated: String,
	var utcModified: String,
	var isProtected: Boolean,
	blobId: BlobId
) : Comparable<Note>, ProtectedSession.NotifyProtectedSessionEnd {
	companion object {
		private const val TAG = "Note"
	}

	var blobId: BlobId = blobId
		private set
	private var blob: Blob? = null
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

	suspend fun isGeoMap(): Boolean {
		val template = getRelation("template")
		return template?.id?.equals(HiddenNotes.GEO_MAP_TEMPLATE.id) == true
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
		if (id == Notes.NONE || inheritableCached) {
			return@withContext
		}
		val paths = Branches.getNotePaths(id) ?: return@withContext
		val allLabels = mutableListOf<Label>()
		val allRelations = mutableListOf<Relation>()
		for (path in paths) {
			val parent = path[0].parentNote
			// inheritable attrs on root are typically not intended to be applied to hidden subtree #3537
			if (parent == Notes.NONE || id == Notes.ROOT || id == Notes.HIDDEN) {
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
		labels.filter { it.name == "iconClass" }.forEach {
			icon = it.value
		}
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
		this.blob = null
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
		titleDecrypted = try {
			ProtectedSession.decrypt(Base64.decode(this.title))!!.decodeToString()
		} catch (e: InvalidCipherTextException) {
			Log.w(TAG, "processing note ${id.rawId()} ", e)
			"[data corrupted]"
		}
		ProtectedSession.addListener(this)
		titleDecrypted!!
	} else {
		title
	}

	fun rawTitle() = title

	fun content() = if (blob == null) {
		null
	} else if (isProtected && !ProtectedSession.isActive()) {
		"[protected]".encodeToByteArray()
	} else if (isProtected && ProtectedSession.isActive()) {
		if (contentDecrypted != null) {
			contentDecrypted
		} else {
			contentDecrypted = try {
				blob!!.decrypt()
			} catch (e: InvalidCipherTextException) {
				"[data corrupted, please report to the developer]".encodeToByteArray()
			}
			ProtectedSession.addListener(this)
			contentDecrypted
		}
	} else {
		blob!!.content
	}

	/**
	 * Get note content encoded for database.
	 * If [isProtected], encrypted and Base64-encoded.
	 */
	fun rawContent() = blob

	/**
	 * Set user-facing note content.
	 *
	 * @param newBlob whether to store the previous content as revision (otherwise: automatic revision)
	 * @return whether content was changed
	 */
	@OptIn(ExperimentalEncodingApi::class)
	suspend fun updateContent(new: ByteArray, newBlob: Boolean = false): Boolean {
		if (isProtected && !ProtectedSession.isActive()) {
			Log.e(TAG, "tried to update protected note without session")
			return false
		}
		if (!newBlob) {
			// only do automatic revision
			val revisionInterval = Option.revisionInterval()!!
			val revisions = revisions()
			val utcThen = revisions.lastOrNull()?.utcDateModified ?: utcCreated
			val utcNow = Cache.utcDateModified()
			val delta = utcNow.parseUtcDate().toEpochSecond() -
					utcThen.parseUtcDate().toEpochSecond()
			if (delta > revisionInterval) {
				NoteRevisions.create(this)
			}
		}
		if (isProtected && ProtectedSession.isActive()) {
			val theNewBlob =
				Blobs.new(Base64.encodeToByteArray(ProtectedSession.encrypt(new)!!), new)
			if (theNewBlob.id == blob?.id) {
				return false
			}
			if (newBlob) {
				NoteRevisions.create(this)
				this.blob = theNewBlob
				this.blobId = blob!!.id
				Notes.refreshDatabaseRow(this)
			} else {
				val oldBlobId = this.blob?.id
				this.blob = theNewBlob
				this.blobId = blob!!.id
				Notes.refreshDatabaseRow(this)
				if (oldBlobId != null) {
					Blobs.delete(oldBlobId)
				}
			}
			this.contentDecrypted = new
		} else {
			val theNewBlob = Blobs.new(new)
			if (theNewBlob.id == blob?.id) {
				return false
			}
			if (newBlob) {
				NoteRevisions.create(this)
				this.blob = theNewBlob
				this.blobId = blob!!.id
				Notes.refreshDatabaseRow(this)
			} else {
				val oldBlobId = this.blob?.id
				this.blob = theNewBlob
				this.blobId = this.blob!!.id
				Notes.refreshDatabaseRow(this)
				if (oldBlobId != null) {
					Blobs.delete(oldBlobId)
				}
			}
		}
		return true
	}

	fun updateContentRaw(new: Blob) {
		if (this.blobId != new.id) {
			error("tried to load wrong blob into note")
		}
		this.blob = new
		this.contentDecrypted = null
	}

	suspend fun changeProtection(protected: Boolean) {
		if (!ProtectedSession.isActive() || this.blob == null) {
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
			val content = this.blob!!.content
			Notes.renameNote(this, title)
			Notes.setNoteContent(id, content.decodeToString())
		}
	}

	suspend fun revisions(): List<NoteRevision> {
		if (revisions != null) {
			return revisions!!
		}
		revisions = NoteRevisions.list(this).toMutableList()
		return revisions!!
	}

	fun revisionsInvalidate() {
		revisions = null
	}

	override fun compareTo(other: Note): Int {
		return id.id.compareTo(other.id.id)
	}

	override fun sessionExpired() {
		contentDecrypted = null
	}

	override fun toString(): String {
		return "Note($id)"
	}
}

data class NoteId(val id: String) : IdLike {
	override fun rawId() = id
	override fun columnName() = "noteId"
	override fun tableName() = "notes"
	override fun toString() = id
}

data class CanvasNoteViewport(val x: Float, val y: Float, val zoom: Float)
