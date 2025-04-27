package eu.fliegendewurst.triliumdroid.database

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.widget.Toast
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.BranchId
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.NoteId
import eu.fliegendewurst.triliumdroid.database.Cache.utcDateModified
import eu.fliegendewurst.triliumdroid.database.Notes.notes
import eu.fliegendewurst.triliumdroid.service.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object Branches {
	private const val TAG = "Branches"

	/**
	 * The root branch.
	 */
	val NONE_ROOT = BranchId("none_root")

	/**
	 * Branches indexed by branch id.
	 */
	val branches: MutableMap<BranchId, Branch> = ConcurrentHashMap()

	/**
	 * Get one possible note path for the provided note.
	 * For deleted notes, this path is probably empty.
	 * The returned path starts with the innermost branch.
	 */
	suspend fun getNotePath(id: NoteId): List<Branch> = withContext(Dispatchers.IO) {
		val l = mutableListOf<Branch>()
		var lastId = id
		while (true) {
			val shouldBreak = DB.rawQuery(
				"SELECT branchId, parentNoteId, isExpanded, notePosition, prefix FROM branches WHERE noteId = ? AND isDeleted = 0 LIMIT 1",
				arrayOf(lastId.id)
			).use {
				if (it.moveToNext()) {
					val branchId = BranchId(it.getString(0))
					val parentId = NoteId(it.getString(1))
					val expanded = it.getInt(2) == 1
					val notePosition = it.getInt(3)
					val prefix = if (!it.isNull(4)) {
						it.getString(4)
					} else {
						null
					}
					if (branches.containsKey(branchId)) {
						l.add(branches[branchId]!!)
					} else {
						val branch =
							Branch(branchId, lastId, parentId, notePosition, prefix, expanded)
						l.add(branch)
					}
					if (parentId == Notes.NONE) { // root reached
						return@use true
					}
					lastId = parentId
				} else {
					return@use true
				}
				return@use false
			}
			if (shouldBreak) {
				break
			}
		}
		return@withContext l
	}

	/**
	 * Return all note paths to a given note.
	 */
	suspend fun getNotePaths(noteId: NoteId): List<List<Branch>>? {
		val branches = Notes.getNote(noteId)?.branches ?: return null
		var possibleBranches = branches.map { x -> listOf(x) }.toMutableList()
		while (true) {
			val newPossibleBranches = mutableListOf<List<Branch>>()
			var progress = false
			for (path in possibleBranches) {
				if (path.last().note == Notes.ROOT) {
					newPossibleBranches.add(path)
					continue
				}
				val note = Notes.getNote(path.last().parentNote)!!
				for (branch in note.branches) {
					val newPath = path.toMutableList()
					newPath.add(branch)
					newPossibleBranches.add(newPath)
					progress = true
				}
			}
			possibleBranches = newPossibleBranches
			if (!progress) {
				break
			}
		}
		return possibleBranches
	}

	suspend fun cloneNote(parentBranch: Branch, note: Note) {
		return cloneNote(parentBranch.note, note.id)
	}

	suspend fun cloneNote(parentNoteId: NoteId, noteId: NoteId) = withContext(Dispatchers.IO) {
		// first, make sure we aren't creating a cycle
		val paths = getNotePaths(parentNoteId) ?: return@withContext
		if (paths.any {
				it.slice(1 until it.size).any { otherBranch -> otherBranch.note == noteId }
			}) {
			Toast.makeText(
				DB.applicationContext,
				R.string.toast_clone_would_cycle,
				Toast.LENGTH_LONG
			).show()
			Log.w(TAG, "refused to create cycle @ parent = $parentNoteId note = $noteId")
			return@withContext
		}
		// create new branch
		val branchId = "${parentNoteId.rawId()}_${noteId.rawId()}"
		// check if it is used
		DB.rawQuery(
			"SELECT 1 FROM branches WHERE branchId = ? AND isDeleted = 0",
			arrayOf(branchId)
		)
			.use {
				if (it.moveToNext()) {
					return@withContext
				}
			}
		// TODO: proper position
		val utc = utcDateModified()
		DB.insertWithConflict(
			"branches",
			SQLiteDatabase.CONFLICT_REPLACE,
			Pair("branchId", branchId),
			Pair("noteId", noteId),
			Pair("parentNoteId", parentNoteId),
			Pair("notePosition", 0),
			Pair("prefix", null),
			Pair("isExpanded", false),
			Pair("isDeleted", false),
			Pair("deleteId", null),
			Pair("utcDateModified", utc)
		)
		registerEntityChangeBranch(Branch(BranchId(branchId), noteId, parentNoteId, 0, null, false))
	}

	suspend fun toggleBranch(branch: Branch) = withContext(Dispatchers.IO) {
		DB.update(branch.id, Pair("isExpanded", !branch.expanded))
		val newValue = !branch.expanded
		branch.expanded = newValue
		branches[branch.id]?.expanded = newValue
	}

	suspend fun moveBranch(branch: Branch, newParent: Branch, newPosition: Int) =
		withContext(Dispatchers.IO) {
			Log.i(
				TAG,
				"moving branch ${branch.id} to new parent ${newParent.note}, pos: ${branch.position} -> $newPosition"
			)
			if (branch.parentNote == newParent.note && branch.position == newPosition) {
				return@withContext // no action needed
			}
			val newId = BranchId("${newParent.note.rawId()}_${branch.note.rawId()}")
			val idChanged = branch.id != newId
			val utc = utcDateModified()
			val res = DB.insertWithConflict(
				newId.tableName(), SQLiteDatabase.CONFLICT_REPLACE,
				Pair("branchId", newId),
				Pair("noteId", branch.note),
				Pair("parentNoteId", newParent.note),
				Pair("notePosition", newPosition),
				Pair("prefix", branch.prefix),
				Pair("isExpanded", branch.expanded),
				Pair("isDeleted", false),
				Pair("deleteId", null),
				Pair("utcDateModified", utc)
			)
			if (res == -1L) {
				Log.e(TAG, "error moving branch!")
			}
			if (idChanged) {
				delete(branch)
				branch.id = newId
			}
			val oldParent = branch.parentNote
			branch.parentNote = newParent.note
			registerEntityChangeBranch(branch)
			notes[oldParent]?.children = null
			notes[newParent.note]?.children = null
			Tree.getTreeData("AND (branches.parentNoteId = \"${oldParent.rawId()}\" OR branches.parentNoteId = \"${newParent.note.rawId()}\")")
		}

	suspend fun delete(branch: Branch) = withContext(Dispatchers.IO) {
		Log.i(TAG, "deleting ${branch.id}")
		DB.update(branch.id, Pair("isDeleted", 1), Pair("deleteId", Util.newDeleteId()))
		registerEntityChangeBranch(branch)
	}
}

private suspend fun registerEntityChangeBranch(branch: Branch) {
	// hash ["branchId", "noteId", "parentNoteId", "prefix"]
	// source: https://github.com/TriliumNext/Notes/blob/develop/src/becca/entities/bbranch.ts
	Cache.registerEntityChange(
		"branches",
		branch.id.id,
		arrayOf(branch.id.id, branch.note.id, branch.parentNote.id, branch.prefix ?: "null")
	)
}
