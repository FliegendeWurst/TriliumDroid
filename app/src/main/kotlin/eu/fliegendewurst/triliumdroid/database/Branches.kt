package eu.fliegendewurst.triliumdroid.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.database.Cache.db
import eu.fliegendewurst.triliumdroid.database.Cache.getTreeData
import eu.fliegendewurst.triliumdroid.database.Cache.utcDateModified
import eu.fliegendewurst.triliumdroid.database.Notes.notes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object Branches {
	private const val TAG = "Branches"

	/**
	 * Branches indexed by branch id.
	 */
	var branches: MutableMap<String, Branch> = ConcurrentHashMap()
		private set

	/**
	 * Get one possible note path for the provided note id.
	 */
	fun getNotePath(id: String): List<Branch> {
		val l = mutableListOf<Branch>()
		var lastId = id
		while (true) {
			db!!.rawQuery(
				"SELECT branchId, parentNoteId, isExpanded, notePosition, prefix FROM branches WHERE noteId = ? AND isDeleted = 0 LIMIT 1",
				arrayOf(lastId)
			).use {
				if (it.moveToNext()) {
					val branchId = it.getString(0)
					val parentId = it.getString(1)
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
					if (parentId == "none") {
						return l
					}
					lastId = parentId
				} else {
					return l
				}
			}
		}
	}

	/**
	 * Return all note paths to a given note.
	 */
	suspend fun getNotePaths(noteId: String): List<List<Branch>>? = withContext(Dispatchers.IO) {
		val branches = Notes.getNote(noteId)?.branches ?: return@withContext null
		var possibleBranches = branches.map { x -> listOf(x) }.toMutableList()
		while (true) {
			val newPossibleBranches = mutableListOf<List<Branch>>()
			var progress = false
			for (path in possibleBranches) {
				if (path.last().note == "root") {
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
		return@withContext possibleBranches
	}

	suspend fun cloneNote(parentBranch: Branch, note: Note) = withContext(Dispatchers.IO) {
		val parentNote = parentBranch.note
		// first, make sure we aren't creating a cycle
		val paths = getNotePaths(parentNote) ?: return@withContext
		if (paths.any { it.any { otherBranch -> otherBranch.note == note.id } }) {
			return@withContext
		}
		// create new branch
		val branchId = "${parentNote}_${note.id}"
		// check if it is used
		db!!.rawQuery("SELECT 1 FROM branches WHERE branchId = ?", arrayOf(branchId))
			.use {
				if (it.moveToNext()) {
					return@withContext
				}
			}
		// TODO: proper position
		val utc = utcDateModified()
		db!!.execSQL(
			"INSERT INTO branches (branchId, noteId, parentNoteId, notePosition, prefix, isExpanded, isDeleted, deleteId, utcDateModified) " +
					"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
			arrayOf(branchId, note.id, parentNote, 0, null, 0, 0, null, utc)
		)
		db!!.registerEntityChangeBranch(Branch(branchId, note.id, parentNote, 0, null, false))
	}

	suspend fun toggleBranch(branch: Branch) = withContext(Dispatchers.IO) {
		db!!.execSQL(
			"UPDATE branches SET isExpanded = ? WHERE branchId = ?",
			arrayOf(
				if (branch.expanded) {
					0
				} else {
					1
				}, branch.id
			)
		)
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
			val newId = "${newParent.note}_${branch.note}"
			val idChanged = branch.id != newId
			val utc = utcDateModified()
			val args = ContentValues()
			args.put("branchId", newId)
			args.put("noteId", branch.note)
			args.put("parentNoteId", newParent.note)
			args.put("notePosition", newPosition)
			args.put("prefix", branch.prefix)
			args.put("isExpanded", branch.expanded)
			args.put("isDeleted", 0)
			args.putNull("deleteId")
			args.put("utcDateModified", utc)
			if (db!!.insertWithOnConflict(
					"branches",
					null,
					args,
					SQLiteDatabase.CONFLICT_REPLACE
				) == -1L
			) {
				Log.e(TAG, "error moving branch!")
			}
			if (idChanged) {
				delete(branch)
				branch.id = newId
			}
			val oldParent = branch.parentNote
			branch.parentNote = newParent.note
			db!!.registerEntityChangeBranch(branch)
			notes[oldParent]?.children = null
			notes[newParent.note]?.children = null
			getTreeData("AND (branches.parentNoteId = \"${oldParent}\" OR branches.parentNoteId = \"${newParent.note}\")")
		}

	suspend fun delete(branch: Branch) = withContext(Dispatchers.IO) {
		Log.i(TAG, "deleting ${branch.id}")
		db!!.execSQL("UPDATE branches SET isDeleted=1 WHERE branchId = ?", arrayOf(branch.id))
		db!!.registerEntityChangeBranch(branch)
	}
}

private suspend fun SQLiteDatabase.registerEntityChangeBranch(
	branch: Branch,
) {
	// hash ["branchId", "noteId", "parentNoteId", "prefix"]
	// source: https://github.com/TriliumNext/Notes/blob/develop/src/becca/entities/bbranch.ts
	registerEntityChange(
		"branches",
		branch.id,
		arrayOf(branch.id, branch.note, branch.parentNote, branch.prefix ?: "null")
	)
}
