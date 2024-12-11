package eu.fliegendewurst.triliumdroid.dialog

import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import eu.fliegendewurst.triliumdroid.MainActivity
import eu.fliegendewurst.triliumdroid.R

object AskForNameDialog {
	fun showDialog(
		activity: MainActivity,
		title: Int,
		defaultText: String,
		callback: (String) -> Unit
	) {
		val dialog = AlertDialog.Builder(activity)
			.setTitle(title)
			.setView(R.layout.dialog_rename_note)
			.create()
		dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
		dialog.show()
		val input = dialog.findViewById<EditText>(R.id.note_title)!!

		if (defaultText != "") {
			input.setText(defaultText)
		}
		input.requestFocus()

		input.setOnEditorActionListener { v, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_SEND) {
				dialog.dismiss()
				callback.invoke(v.text.toString())
				return@setOnEditorActionListener true
			}
			false
		}

		dialog.findViewById<Button>(R.id.button_rename_note)!!.setOnClickListener {
			dialog.dismiss()
			callback.invoke(input.text.toString())
		}
	}
}