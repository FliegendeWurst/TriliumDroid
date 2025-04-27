package eu.fliegendewurst.triliumdroid.database

import android.util.Log
import eu.fliegendewurst.triliumdroid.data.BlobId
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.BranchId
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.NoteId
import eu.fliegendewurst.triliumdroid.database.Branches.branches
import eu.fliegendewurst.triliumdroid.database.Notes.notes
import eu.fliegendewurst.triliumdroid.service.Icon
import eu.fliegendewurst.triliumdroid.service.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Helper functions for inspecting the note tree.
 */
object Tree {
	private const val TAG: String = "Tree"
	private val LOCK = Mutex()

	/**
	 * Populate the tree data cache.
	 */
	suspend fun getTreeData(filter: String) {
		LOCK.withLock {
			getTreeDataInternal(filter)
		}
	}

	/**
	 * Get the note tree starting at the id and level.
	 */
	suspend fun getTreeList(branchId: BranchId, lvl: Int): MutableList<Pair<Branch, Int>> {
		return withContext(Dispatchers.IO) {
			LOCK.withLock {
				getTreeListInternal(branchId, lvl)
			}
		}
	}

	private fun getTreeListInternal(branchId: BranchId, lvl: Int): MutableList<Pair<Branch, Int>> {
		val list = ArrayList<Pair<Branch, Int>>()
		val current = branches[branchId] ?: return list
		list.add(Pair(current, lvl))
		if (!current.expanded) {
			return list
		}
		for (children in notes[current.note]!!.children.orEmpty()) {
			list.addAll(getTreeListInternal(children.id, lvl + 1))
		}
		return list
	}

	private suspend fun getTreeDataInternal(filter: String) = withContext(Dispatchers.IO) {
		val startTime = System.currentTimeMillis()
		if (filter == "") {
			// regenerate all!
			notes.values.forEach {
				it.branches = mutableListOf<Branch>()
				it.children = null
			}
		}
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
				n.branches.removeIf { branch -> branch.id == branchId }
				n.branches.add(b)
				n.branches.sortBy { br -> br.position } // TODO: sort by date instead?
				clones.add(Pair(Pair(parentNoteId, noteId), branchId))
			}
			for (p in clones) {
				val parentNoteId = p.first.first
				val b = branches[p.second]
				if (parentNoteId == Notes.NONE || b == null) {
					continue
				}
				val parentNote = notes[parentNoteId]
				if (parentNote != null) {
					if (parentNote.children == null) {
						parentNote.children = TreeSet()
					}
					parentNote.children!!.removeIf { it.id == b.id }
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
}
