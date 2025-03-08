package eu.fliegendewurst.triliumdroid.dialog

import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.databinding.ItemNoteIconBinding
import eu.fliegendewurst.triliumdroid.service.Icon
import eu.fliegendewurst.triliumdroid.util.ListRecyclerAdapter
import kotlin.math.nextDown


object NoteIconDialog {
	private const val TAG = "NoteIconDialog"

	fun showDialogReturningIcon(
		activity: AppCompatActivity,
		callback: (String) -> Unit
	) {
		Log.d(TAG, "showing note icon dialog")
		val dialog = AlertDialog.Builder(activity)
			.setTitle(R.string.title_select_note_icon)
			.setView(R.layout.dialog_note_icon)
			.create()
		dialog.show()

		val displayMetrics: DisplayMetrics = activity.resources.displayMetrics
		val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
		val cols = ((screenWidthDp * .8F) / 48F).nextDown().toInt()

		val listener = { view: View ->
			val iconSelected = (view as Button).text
			dialog.dismiss()
			callback.invoke(iconSelected.toString())
		}

		val list = dialog.findViewById<RecyclerView>(R.id.list_all_icons)!!
		val adapter = ListRecyclerAdapter({ view, x: String ->
			view.icon.text = x
			view.icon.setOnClickListener(listener)
		}) { parent ->
			return@ListRecyclerAdapter ItemNoteIconBinding.inflate(
				LayoutInflater.from(
					parent.context
				), parent, false
			)
		}
		adapter.submitList(Icon.getAllUnicodeCharacters())
		list.setLayoutManager(GridLayoutManager(activity, cols))
		list.adapter = adapter

		val list2 = dialog.findViewById<RecyclerView>(R.id.list_frequent_icons)!!
		val adapter2 = ListRecyclerAdapter({ view, x: String ->
			view.icon.text = x
			view.icon.setOnClickListener(listener)
		}) { parent ->
			return@ListRecyclerAdapter ItemNoteIconBinding.inflate(
				LayoutInflater.from(
					parent.context
				), parent, false
			)
		}
		adapter2.submitList(Icon.iconStatistics.orEmpty().mapNotNull { it.first })
		list2.setLayoutManager(GridLayoutManager(activity, cols))
		list2.adapter = adapter2
	}
}
