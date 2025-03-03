package eu.fliegendewurst.triliumdroid.dialog

import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.TreeItemAdapter
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.database.Cache
import eu.fliegendewurst.triliumdroid.database.Notes
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object JumpToNoteDialog {
	fun showDialog(activity: MainActivity) {
		showDialogReturningNote(activity, R.string.jump_to_dialog) {
			activity.navigateTo(runBlocking { Notes.getNote(it.note)!! })
		}
	}

	fun showDialogReturningNote(
		activity: AppCompatActivity,
		title: Int,
		callback: (Branch) -> Unit
	) {
		val dialog = AlertDialog.Builder(activity)
			.setTitle(title)
			.setView(R.layout.dialog_jump)
			.create()
		dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
		dialog.show()
		val input = dialog.findViewById<EditText>(R.id.jump_input)!!
		val list = dialog.findViewById<RecyclerView>(R.id.jump_to_list)!!
		val adapter2 = TreeItemAdapter({
			dialog.dismiss()
			callback.invoke(it)
		}, {
			// long click has no effect
		})
		list.adapter = adapter2
		input.requestFocus()
		input.addTextChangedListener {
			val searchString = input.text.toString()
			if (searchString.length < 3) {
				adapter2.submitList(emptyList())
				return@addTextChangedListener
			}
			activity.lifecycleScope.launch {
				val results = Cache.getJumpToResults(searchString)
				val stuff = results.map {
					Pair(
						Branch(MainActivity.JUMP_TO_NOTE_ENTRY, it.id, it.id, 0, null, false),
						0
					)
				}.toList()
				if (adapter2.currentList != stuff) {
					adapter2.submitList(stuff)
				}
			}
		}
	}
}
