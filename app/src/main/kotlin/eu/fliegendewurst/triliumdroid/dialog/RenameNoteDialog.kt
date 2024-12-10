package eu.fliegendewurst.triliumdroid.dialog

import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import eu.fliegendewurst.triliumdroid.Cache
import eu.fliegendewurst.triliumdroid.MainActivity
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.Note
import java.util.*

object RenameNoteDialog {
	fun showDialog(activity: MainActivity, currentNote: Note) {
		val dialog = AlertDialog.Builder(activity)
			.setTitle(R.string.dialog_rename_note)
			.setView(R.layout.dialog_rename_note)
			.create()
		dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
		dialog.show()
		val input = dialog.findViewById<EditText>(R.id.note_title)!!

		input.setText(currentNote.title)
		input.requestFocus()

		input.setOnEditorActionListener { v, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_SEND) {
				done(activity, dialog, v.text.toString(), currentNote)
				return@setOnEditorActionListener true
			}
			false
		}

		dialog.findViewById<Button>(R.id.button_rename_note)!!.setOnClickListener {
			done(activity, dialog, input.text.toString(), currentNote)
		}
	}

	private fun done(
		activity: MainActivity,
		dialog: AlertDialog,
		title: String,
		currentNote: Note
	) {
		Cache.renameNote(currentNote, title)
		dialog.dismiss()
		activity.refreshTree()
		activity.refreshTitle()
	}
}