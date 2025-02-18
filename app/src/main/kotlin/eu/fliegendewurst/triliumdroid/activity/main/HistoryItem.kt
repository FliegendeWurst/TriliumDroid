package eu.fliegendewurst.triliumdroid.activity.main

import eu.fliegendewurst.triliumdroid.fragment.NoteEditFragment
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.fragment.NavigationFragment
import eu.fliegendewurst.triliumdroid.fragment.NoteMapFragment

/**
 * One screen (note, note map, etc.) visible to the user.
 */
abstract class HistoryItem {
	/**
	 * Try to restore this history item. Returns whether successful.
	 */
	abstract fun restore(activity: MainActivity): Boolean

	abstract fun noteId(): String
	abstract fun branch(): Branch?

	abstract fun setBranch(branch: Branch)
}

class NoteItem(private val note: Note, private var branch: Branch?) : HistoryItem() {
	override fun restore(activity: MainActivity): Boolean {
		if (note.id == "DELETED") {
			return false
		}
		activity.load(note, branch)
		return true
	}

	override fun noteId(): String {
		return note.id
	}

	override fun branch(): Branch? {
		return branch
	}

	override fun setBranch(branch: Branch) {
		this.branch = branch
	}
}

class NoteEditItem(val note: Note) : HistoryItem() {
	override fun restore(activity: MainActivity): Boolean {
		if (note.id == "DELETED") {
			return false
		}
		val frag = NoteEditFragment()
		frag.loadLater(note.id)
		activity.showFragment(frag, true)
		return true
	}

	override fun noteId(): String {
		return note.id
	}

	override fun branch(): Branch? {
		return null
	}

	override fun setBranch(branch: Branch) {
	}
}

class NoteMapItem(val note: Note?) : HistoryItem() {
	override fun restore(activity: MainActivity): Boolean {
		if (note == null) {
			val fragMap = NoteMapFragment()
			fragMap.loadLaterGlobal()
			activity.showFragment(fragMap, true)
			return true
		} else if (note.id == "DELETED") {
			return false
		} else {
			val frag = NoteMapFragment()
			frag.loadLater(note.id)
			activity.showFragment(frag, true)
			return true
		}
	}

	override fun noteId(): String {
		return note?.id ?: "root"
	}

	override fun branch(): Branch? {
		return null
	}

	override fun setBranch(branch: Branch) {
	}
}

class NavigationItem(private val note: Note, private var branchStart: Branch) : HistoryItem() {
	override fun restore(activity: MainActivity): Boolean {
		val frag = NavigationFragment()
		activity.showFragment(frag, true)
		frag.showFor(note, branchStart)
		return true
	}

	override fun noteId(): String {
		return note.id
	}

	override fun branch(): Branch {
		return branchStart
	}

	override fun setBranch(branch: Branch) {
		// not exactly applicable
		branchStart = branch
	}
}
