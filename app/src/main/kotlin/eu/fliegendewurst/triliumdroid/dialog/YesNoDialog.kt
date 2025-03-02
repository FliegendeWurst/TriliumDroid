package eu.fliegendewurst.triliumdroid.dialog

import android.content.Context
import androidx.appcompat.app.AlertDialog

object YesNoDialog {
	fun show(context: Context, titleId: Int, textId: Int, callbackOk: () -> Unit) {
		AlertDialog.Builder(context)
			.setTitle(titleId)
			.setMessage(textId)
			.setIconAttribute(android.R.attr.alertDialogIcon)
			.setPositiveButton(
				android.R.string.ok
			) { _, _ ->
				callbackOk.invoke()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}
}
