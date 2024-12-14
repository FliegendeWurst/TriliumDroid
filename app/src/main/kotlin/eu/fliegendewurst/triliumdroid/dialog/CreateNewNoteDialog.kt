package eu.fliegendewurst.triliumdroid.dialog

import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import eu.fliegendewurst.triliumdroid.Cache
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.data.Note
import java.util.*

object CreateNewNoteDialog {
	fun showDialog(activity: MainActivity, createAsChild: Boolean, currentNote: Note) {
		val dialog = AlertDialog.Builder(activity)
			.setTitle(R.string.dialog_create_new_note)
			.setView(R.layout.dialog_create_new_note)
			.create()
		dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
		dialog.show()
		val input = dialog.findViewById<EditText>(R.id.note_title)!!

		input.requestFocus()

		input.setOnEditorActionListener { v, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_SEND) {
				done(activity, dialog, v.text.toString(), createAsChild, currentNote)
				return@setOnEditorActionListener true
			}
			false
		}

		dialog.findViewById<Button>(R.id.button_create_note)!!.setOnClickListener {
			done(activity, dialog, input.text.toString(), createAsChild, currentNote)
		}
	}

	private fun done(
		activity: MainActivity,
		dialog: AlertDialog,
		title: String,
		createAsChild: Boolean,
		currentNote: Note
	) {
		val note = if (createAsChild) {
			Cache.createChildNote(currentNote, title)
		} else {
			Cache.createSiblingNote(currentNote, title)
		}
		dialog.dismiss()
		activity.navigateTo(note)
		activity.refreshTree()
	}
}