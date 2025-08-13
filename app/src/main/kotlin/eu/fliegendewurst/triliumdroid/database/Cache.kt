package eu.fliegendewurst.triliumdroid.database

import android.annotation.SuppressLint
import android.icu.text.SimpleDateFormat
import android.util.Log
import androidx.core.database.getStringOrNull
import eu.fliegendewurst.triliumdroid.BuildConfig
import eu.fliegendewurst.triliumdroid.data.AttributeId
import eu.fliegendewurst.triliumdroid.data.BlobId
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.NoteId
import eu.fliegendewurst.triliumdroid.data.Relation
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
		if (Preferences.readOnlyMode()) {
			Log.w(TAG, "read-only mode ignoring entity change!")
			return@withContext
		}
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
		// already existing entity_change for this (name, ID) pair
		DB.internalGetDatabase()!!
			.delete("entity_changes", "entityName = ? AND entityId = ?", arrayOf(table, id))
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
	 * Use [Note.computeChildren] instead
	 */
	suspend fun getChildren(noteId: NoteId) {
		Tree.getTreeData("AND (branches.parentNoteId = '${noteId.id}' OR branches.noteId = '${noteId.id}')")
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
				val attrName = it.getStringOrNull(2)
				val attrValue = it.getStringOrNull(3)
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
				if (attrId != null && attrName != null && attrValue != null &&
					!attrValue.startsWith('_') &&
					!attrName.startsWith("child:")
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

	suspend fun getGeoMapPins(id: NoteId): List<GeoMapPin> = withContext(Dispatchers.IO) {
		val l = mutableListOf<GeoMapPin>()
		val geolocations = mutableMapOf<String, String>()
		val icons = mutableMapOf<String, String>()
		val colors = mutableMapOf<String, String>()
		val titles = mutableMapOf<String, String>()
		DB.rawQuery(
			"SELECT noteId,attributes.name,attributes.value,notes.title FROM attributes INNER JOIN notes USING (noteId) INNER JOIN branches USING (noteId) WHERE branches.parentNoteId = ? AND (attributes.name = 'geolocation' OR attributes.name = 'iconClass' OR attributes.name = 'color')",
			arrayOf(id.rawId())
		).use {
			while (it.moveToNext()) {
				val id = it.getString(0)
				val name = it.getString(1)
				val value = it.getString(2)
				val title = it.getString(3)
				titles.put(id, title)
				when (name) {
					"geolocation" -> {
						geolocations.put(id, value)
					}

					"iconClass" -> {
						icons.put(id, value)
					}

					"colors" -> {
						colors.put(id, value)
					}
				}
			}
		}
		for (loc in geolocations) {
			val id = loc.key
			val latLng = loc.value.split(',')
			l.add(
				GeoMapPin(
					id,
					titles[id] ?: continue,
					latLng[0].toDouble(),
					latLng[1].toDouble(),
					icons[id],
					colors[id]
				)
			)
		}
		l
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

	data class GeoMapPin(
		val noteId: String,
		val title: String,
		val lat: Double,
		val lng: Double,
		val iconClass: String?,
		val color: String?
	)

	// see https://github.com/TriliumNext/Notes/tree/develop/db
	// and https://github.com/TriliumNext/Notes/blob/develop/src/services/app_info.ts
	object Versions {
		const val DATABASE_VERSION_0_59_4 = 213
		const val DATABASE_VERSION_0_60_4 = 214
		const val DATABASE_VERSION_0_61_5 = 225
		const val DATABASE_VERSION_0_62_3 = 227
		const val DATABASE_VERSION_0_63_3 = 228 // same up to 0.92.4
		const val DATABASE_VERSION_0_92_6 = 229 // same up to 0.93.0
		const val DATABASE_VERSION_0_94_0 = 231 // same up to 0.94.1
		const val DATABASE_VERSION_0_95_0 = 232
		const val DATABASE_VERSION_0_97_2 = 233

		const val SYNC_VERSION_0_59_4 = 29
		const val SYNC_VERSION_0_60_4 = 29
		const val SYNC_VERSION_0_62_3 = 31
		const val SYNC_VERSION_0_63_3 = 32
		const val SYNC_VERSION_0_90_12 = 33
		const val SYNC_VERSION_0_91_6 = 34 // same up to 0.93.0
		const val SYNC_VERSION_0_94_0 = 35 // same up to 0.94.1
		const val SYNC_VERSION_0_95_0 = 36
		const val SYNC_VERSION_0_97_2 = 36

		val SUPPORTED_SYNC_VERSIONS: Set<Int> = setOf(
			SYNC_VERSION_0_97_2,
			SYNC_VERSION_0_95_0,
			SYNC_VERSION_0_91_6,
			SYNC_VERSION_0_90_12,
			SYNC_VERSION_0_63_3,
		)
		val SUPPORTED_DATABASE_VERSIONS: Set<Int> = setOf(
			DATABASE_VERSION_0_63_3,
			DATABASE_VERSION_0_92_6,
			DATABASE_VERSION_0_95_0,
			DATABASE_VERSION_0_97_2
		)

		const val DATABASE_VERSION = DATABASE_VERSION_0_97_2
		const val DATABASE_NAME = "Document.db"

		// sync version is largely irrelevant
		const val SYNC_VERSION = SYNC_VERSION_0_97_2
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
