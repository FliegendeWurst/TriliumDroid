package eu.fliegendewurst.triliumdroid.dialog

import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.TreeItemAdapter
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.data.Branch

object SelectNoteDialog {
	fun showDialogReturningNote(
		activity: MainActivity,
		notes: List<Branch>,
		callback: (Branch) -> Unit
	) {
		val dialog = AlertDialog.Builder(activity)
			.setTitle(R.string.dialog_select_sibling_note)
			.setView(R.layout.dialog_select_note)
			.create()
		dialog.show()
		val list = dialog.findViewById<RecyclerView>(R.id.list_note_selection)!!
		val adapter2 = TreeItemAdapter({
			dialog.dismiss()
			callback.invoke(it)
		}, {
			// long click has no effect
		})
		adapter2.submitList(notes.map { x -> Pair(x, 0) })
		list.adapter = adapter2
		val buttonLast = dialog.findViewById<Button>(R.id.button_insert_last)!!
		buttonLast.setOnClickListener {
			dialog.dismiss()
			callback.invoke(notes.last())
		}
	}
}