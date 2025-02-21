package eu.fliegendewurst.triliumdroid.activity.main

import eu.fliegendewurst.triliumdroid.data.Branch

class HistoryList {
	private var items: MutableList<HistoryItem> = mutableListOf(
		StartItem()
	)

	fun addAndRestore(item: HistoryItem, activity: MainActivity) {
		// Note Navigation done? Remove from history.
		val last = items.lastOrNull()
		if (last is NavigationItem || last is StartItem) {
			items.removeAt(items.size - 1)
		}
		items.add(item)
		items.last().restore(activity)
	}

	fun goBack(activity: MainActivity): Boolean {
		// remove currently active item
		if (items.isNotEmpty()) {
			items.removeAt(items.size - 1)
		}
		while (true) {
			if (items.isEmpty()) {
				return true
			} else {
				val entry = items[items.size - 1]
				if (entry.restore(activity)) {
					return false
				} else {
					items.removeAt(items.size - 1)
				}
			}
		}
	}

	fun isEmpty(): Boolean = items.isEmpty() || (items.size == 1 && items.first() is StartItem)

	fun noteId(): String = items.last().noteId()
	fun branch(): Branch? = items.last().branch()
	fun setBranch(x: Branch) = items.last().setBranch(x)
}
