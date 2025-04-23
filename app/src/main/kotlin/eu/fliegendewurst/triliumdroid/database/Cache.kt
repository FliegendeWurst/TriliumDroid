package eu.fliegendewurst.triliumdroid.database

import android.annotation.SuppressLint
import android.icu.text.SimpleDateFormat
import android.util.Log
import androidx.core.database.getStringOrNull
import eu.fliegendewurst.triliumdroid.BuildConfig
import eu.fliegendewurst.triliumdroid.data.AttributeId
import eu.fliegendewurst.triliumdroid.data.BlobId
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.BranchId
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.NoteId
import eu.fliegendewurst.triliumdroid.data.Relation
import eu.fliegendewurst.triliumdroid.database.Branches.branches
import eu.fliegendewurst.triliumdroid.database.Notes.notes
import eu.fliegendewurst.triliumdroid.service.Icon
import eu.fliegendewurst.triliumdroid.service.Util
import eu.fliegendewurst.triliumdroid.util.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object Cache {
	private const val TAG: String = "Cache"

	suspend fun registerEntityChange(
		table: String,
		id: String,
		toHash: Array<String?>,
		isErased: Boolean = false
	) {
		registerEntityChange(table, id, toHash.map { it?.encodeToByteArray() }, isErased)
	}

	suspend fun registerEntityChange(
		table: String,
		id: String,
		toHash: List<ByteArray?>,
		isErased: Boolean = false
	) = withContext(Dispatchers.IO) {
		val utc = utcDateModified()
		// https://github.com/TriliumNext/Notes/blob/v0.93.0/src/becca/entities/abstract_becca_entity.ts#L63-L76
		val md = MessageDigest.getInstance("SHA-1")
		for (h in toHash) {
			md.update('|'.code.toByte())
			if (h != null) {
				md.update(h)
			} else {
				md.update("null".encodeToByteArray())
			}
		}
		// TODO if isDeleted, add |deleted
		val sha1hash = md.digest()
		val hash = Base64.encode(sha1hash).substring(0 until 10)
		// TODO: correct hash for blobs?
		DB.insert(
			"entity_changes",
			Pair("entityName", table),
			Pair("entityId", id),
			Pair("hash", hash),
			Pair("isErased", isErased),
			Pair("changeId", Util.randomString(12)),
			Pair("componentId", "Android"),
			Pair("instanceId", Preferences.instanceId()),
			Pair("isSynced", true),
			Pair("utcDateChanged", utc)
		)
	}

	suspend fun getNotesWithAttribute(attributeName: String, attributeValue: String?): List<Note> =
		withContext(Dispatchers.IO) {
			var query =
				"SELECT noteId FROM notes INNER JOIN attributes USING (noteId) WHERE attributes.name = ? AND attributes.isDeleted = 0"
			if (attributeValue != null) {
				query += " AND attributes.value = ?"
			}
			DB.rawQuery(
				query,
				if (attributeValue != null) {
					arrayOf(attributeName, attributeValue)
				} else {
					arrayOf(attributeName)
				}
			).use {
				val l = mutableListOf<Note>()
				while (it.moveToNext()) {
					val id = NoteId(it.getString(0))
					l.add(Notes.getNote(id)!!)
				}
				return@withContext l
			}
		}

	suspend fun getJumpToResults(input: String): List<Note> = withContext(Dispatchers.IO) {
		val notes = mutableListOf<Note>()
		DB.rawQuery(
			"SELECT noteId, mime, title, type FROM notes WHERE isDeleted = 0 AND title LIKE ? LIMIT 50",
			arrayOf("%$input%")
		).use {
			while (it.moveToNext()) {
				val note = Note(
					NoteId(it.getString(0)),
					it.getString(1),
					it.getString(2),
					it.getString(3),
					false,
					null,
					"INVALID",
					"INVALID",
					"INVALID",
					"INVALID",
					false,
					BlobId("INVALID")
				)
				notes.add(note)
			}
		}
		return@withContext notes
	}

	/**
	 * Populate the tree data cache.
	 */
	suspend fun getTreeData(filter: String) = withContext(Dispatchers.IO) {
		val startTime = System.currentTimeMillis()
		DB.rawQuery(
			"SELECT branchId, " +
					"branches.noteId, " +
					"parentNoteId, " +
					"notePosition, " +
					"prefix, " +
					"isExpanded, " +
					"mime, " +
					"title, " +
					"notes.type, " +
					"notes.dateCreated, " +
					"notes.dateModified, " +
					"notes.isProtected, " +
					"notes.utcDateCreated, " +
					"notes.utcDateModified, " +
					"notes.blobId " +
					"FROM branches INNER JOIN notes USING (noteId) WHERE notes.isDeleted = 0 AND branches.isDeleted = 0 $filter",
			arrayOf()
		).use {
			val clones = mutableListOf<Pair<Pair<NoteId, NoteId>, BranchId>>()
			while (it.moveToNext()) {
				val branchId = BranchId(it.getString(0))
				val noteId = NoteId(it.getString(1))
				val parentNoteId = NoteId(it.getString(2))
				val notePosition = it.getInt(3)
				val prefix = if (!it.isNull(4)) {
					it.getString(4)
				} else {
					null
				}
				val isExpanded = it.getInt(5) == 1
				val mime = it.getString(6)
				val title = it.getString(7)
				val type = it.getString(8)
				val dateCreated = it.getString(9)
				val dateModified = it.getString(10)
				val isProtected = it.getInt(11) != 0
				val utcDateCreated = it.getString(12)
				val utcDateModified = it.getString(13)
				val blobId = BlobId(it.getString(14))
				val b = Branch(
					branchId,
					noteId,
					parentNoteId,
					notePosition,
					prefix,
					isExpanded
				)
				branches[branchId] = b
				val n = notes.computeIfAbsent(noteId) {
					Note(
						noteId,
						mime,
						title,
						type,
						false,
						null,
						dateCreated,
						dateModified,
						utcDateCreated,
						utcDateModified,
						isProtected,
						blobId
					)
				}
				if (n.branches.none { branch -> branch.id == branchId }) {
					n.branches.add(b)
					n.branches.sortBy { br -> br.position } // TODO: sort by date instead?
				}
				clones.add(Pair(Pair(parentNoteId, noteId), branchId))
			}
			for (p in clones) {
				val parentNoteId = p.first.first
				val b = branches[p.second]
				if (parentNoteId == Notes.NONE) {
					continue
				}
				val parentNote = notes[parentNoteId]
				if (parentNote != null) {
					if (parentNote.children == null) {
						parentNote.children = TreeSet()
					}
					parentNote.children!!.add(b)
				} else {
					Log.w(TAG, "getTreeData() failed to find $parentNoteId in ${notes.size} notes")
				}
			}
		}
		val query1 = System.currentTimeMillis() - startTime
		val query2Start = System.currentTimeMillis()
		val stats = if (filter == "") {
			mutableMapOf<String, Int>()
		} else {
			null
		}
		DB.rawQuery(
			"SELECT notes.noteId, attributes.value " +
					"FROM attributes " +
					"INNER JOIN notes USING (noteId) " +
					"INNER JOIN branches USING (noteId) " +
					"WHERE notes.isDeleted = 0 AND attributes.isDeleted = 0 " +
					"AND attributes.name = 'iconClass' AND attributes.type = 'label' $filter",
			arrayOf()
		).use {
			while (it.moveToNext()) {
				val noteId = NoteId(it.getString(0))
				val noteIcon = it.getString(1)
				notes[noteId]!!.icon = noteIcon
				// gather statistics on note icons of user notes
				if (stats != null && Util.isRegularId(noteId)) {
					stats[noteIcon] = stats.getOrDefault(noteIcon, 0) + 1
				}
			}
		}
		if (stats != null) {
			Icon.iconStatistics =
				stats.entries.sortedWith(Comparator.comparingInt<MutableMap.MutableEntry<String, Int>?> { -it.value }
					.thenComparing { it -> it.key })
					.map { Pair(Icon.getUnicodeCharacter(it.key), it.value) }
		}
		val query2 = System.currentTimeMillis() - query2Start
		if (query1 + query2 > 50) {
			Log.w(TAG, "slow getTreeData() $query1 ms + $query2 ms")
		}
	}

	/**
	 * Use [Note.computeChildren] instead
	 */
	suspend fun getChildren(noteId: NoteId) {
		getTreeData("AND (branches.parentNoteId = '${noteId.id}' OR branches.noteId = '${noteId.id}')")
	}

	/**
	 * Get the note tree starting at the id and level.
	 */
	fun getTreeList(branchId: BranchId, lvl: Int): MutableList<Pair<Branch, Int>> {
		val list = ArrayList<Pair<Branch, Int>>()
		val current = branches[branchId] ?: return list
		list.add(Pair(current, lvl))
		if (!current.expanded) {
			return list
		}
		for (children in notes[current.note]!!.children.orEmpty()) {
			list.addAll(getTreeList(children.id, lvl + 1))
		}
		return list
	}

	/**
	 * Get all notes with their relations.
	 * WARNING: the returned notes ONLY have their title and relations set.
	 */
	suspend fun getAllNotesWithRelations(): List<Note> = withContext(Dispatchers.IO) {
		val list = mutableListOf<Note>()
		val relations = mutableListOf<Triple<NoteId, NoteId, Pair<String, AttributeId>>>()
		DB.rawQuery(
			"SELECT " +
					"noteId, " + // 0
					"title," + // 1
					"attributes.name," + // 2
					"attributes.value," + // 3
					"attributes.attributeId " + // 4
					"FROM notes " +
					"LEFT JOIN attributes USING (noteId) " +
					"WHERE notes.isDeleted = 0 " +
					"AND (attributes.isDeleted = 0 OR attributes.isDeleted IS NULL) " +
					"AND (attributes.type == 'relation' OR attributes.type IS NULL) " +
					"AND SUBSTR(noteId, 1, 1) != '_' " +
					"ORDER BY noteId",
			arrayOf()
		).use {
			var currentNote: Note? = null
			// source, target, name
			while (it.moveToNext()) {
				val id = NoteId(it.getString(0))
				val title = it.getString(1)
				val attrName = it.getString(2)
				val attrValue = it.getString(3)
				val attrIdRaw = it.getStringOrNull(4)
				val attrId = if (attrIdRaw != null) {
					AttributeId(attrIdRaw)
				} else {
					null
				}
				if (currentNote == null || currentNote.id != id) {
					if (currentNote != null) {
						list.add(currentNote)
					}
					currentNote = Note(
						id,
						"",
						title,
						"",
						false,
						null,
						"",
						"",
						"",
						"",
						false,
						BlobId("INVALID")
					)
				}
				if (attrValue != null && !attrValue.startsWith('_') &&
					!attrName.startsWith("child:") && attrId != null
				) {
					relations.add(Triple(id, NoteId(attrValue), Pair(attrName, attrId)))
				}
			}
			if (currentNote != null) {
				list.add(currentNote)
			}
		}
		val notesById = list.associateBy { x -> x.id }
		val relationsById = mutableMapOf<NoteId, MutableList<Relation>>()
		val relationsByIdIncoming = mutableMapOf<NoteId, MutableList<Relation>>()
		for (rel in relations) {
			if (relationsById[rel.first] == null) {
				relationsById[rel.first] = mutableListOf()
			}
			if (relationsByIdIncoming[rel.second] == null) {
				relationsByIdIncoming[rel.second] = mutableListOf()
			}
			if (!notesById.containsKey(rel.second)) {
				// relation points to deleted note??
				Log.w(TAG, "relation from ${rel.first} to deleted ${rel.second} found")
				continue
			}
			val relation = Relation(
				rel.third.second,
				notesById[rel.second]!!, rel.third.first,
				inheritable = false,
				promoted = false,
				multi = false
			)
			relationsById[rel.first]!!.add(relation)
			relationsByIdIncoming[rel.second]!!.add(relation)
		}
		for (v in relationsById) {
			notesById[v.key]!!.setRelations(v.value)
			notesById[v.key]!!.incomingRelations = relationsByIdIncoming[v.key]
		}
		return@withContext list
	}

	@SuppressLint("SimpleDateFormat")
	private val localTime: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ")

	fun dateModified(): String {
		return localTime.format(Calendar.getInstance().time)
	}

	/**
	 * Get time formatted as YYYY-MM-DD HH:MM:SS.sssZ
	 */
	fun utcDateModified(): String {
		return DateTimeFormatter.ISO_INSTANT.format(OffsetDateTime.now(ZoneOffset.UTC))
			.replace('T', ' ')
	}

	// see https://github.com/TriliumNext/Notes/tree/develop/db
	// and https://github.com/TriliumNext/Notes/blob/develop/src/services/app_info.ts
	object Versions {
		const val DATABASE_VERSION_0_59_4 = 213
		const val DATABASE_VERSION_0_60_4 = 214
		const val DATABASE_VERSION_0_61_5 = 225
		const val DATABASE_VERSION_0_62_3 = 227
		const val DATABASE_VERSION_0_63_3 = 228 // same up to 0.92.4
		const val DATABASE_VERSION_0_92_6 = 229 // same up to 0.93.0

		const val SYNC_VERSION_0_59_4 = 29
		const val SYNC_VERSION_0_60_4 = 29
		const val SYNC_VERSION_0_62_3 = 31
		const val SYNC_VERSION_0_63_3 = 32
		const val SYNC_VERSION_0_90_12 = 33
		const val SYNC_VERSION_0_91_6 = 34 // same up to 0.93.0

		val SUPPORTED_SYNC_VERSIONS: Set<Int> = setOf(
			SYNC_VERSION_0_91_6,
			SYNC_VERSION_0_90_12,
			SYNC_VERSION_0_63_3,
		)
		val SUPPORTED_DATABASE_VERSIONS: Set<Int> = setOf(
			DATABASE_VERSION_0_63_3,
			DATABASE_VERSION_0_92_6,
		)

		const val DATABASE_VERSION = DATABASE_VERSION_0_92_6
		const val DATABASE_NAME = "Document.db"

		// sync version is largely irrelevant
		const val SYNC_VERSION = SYNC_VERSION_0_91_6
		const val APP_VERSION = BuildConfig.VERSION_NAME
	}
}

fun Boolean.boolToIntValue(): Int = if (this) {
	1
} else {
	0
}

fun Boolean.boolToIntString(): String = if (this) {
	"1"
} else {
	"0"
}

fun Int.intValueToBool(): Boolean = this == 1

fun String.parseUtcDate(): OffsetDateTime =
	OffsetDateTime.parse(this.replace(' ', 'T'))
