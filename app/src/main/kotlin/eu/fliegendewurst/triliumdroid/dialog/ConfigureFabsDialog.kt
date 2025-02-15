package eu.fliegendewurst.triliumdroid.dialog

import android.content.SharedPreferences
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ListView
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.activity.SetupActivity
import eu.fliegendewurst.triliumdroid.util.ListAdapter

object ConfigureFabsDialog {
	/**
	 * See [eu.fliegendewurst.triliumdroid.activity.main.MainActivity.performAction].
	 */
	val actions = listOf(
		"showNoteTree",
		"jumpToNote",
		"editNote",
		"shareNote",
		"deleteNote",
		"noteMap",
		"sync"
	)

	fun init(prefs: SharedPreferences) {
		for (action in actions) {
			if (getPref(prefs, action) == null) {
				when (action) {
					"showNoteTree" -> {
						setPref(prefs, action, left = true, right = false, show = false)
					}

					"jumpToNote" -> {
						setPref(prefs, action, left = false, right = true, show = false)
					}

					else -> {
						setPref(prefs, action, left = false, right = false, show = true)
					}
				}
			}
		}
	}

	fun showDialog(
		activity: SetupActivity,
		prefs: SharedPreferences,
		callback: () -> Unit,
	) {
		val dialog = AlertDialog.Builder(activity)
			.setTitle(R.string.dialog_configure_fabs)
			.setView(R.layout.dialog_configure_fabs)
			.create()
		dialog.show()

		dialog.findViewById<Button>(R.id.button_setup_fabs)!!.setOnClickListener {
			dialog.dismiss()
		}
		dialog.setOnDismissListener {
			callback.invoke()
		}

		val list = dialog.findViewById<ListView>(R.id.list_fab_actions)!!
		val labels = activity.resources.getStringArray(R.array.fabs)
		list.adapter = ListAdapter(actions) { action, convertView: View? ->
			var vi = convertView
			if (vi == null) {
				vi = dialog.layoutInflater.inflate(
					R.layout.item_fab_action,
					list,
					false
				)
			}
			vi!!.findViewById<TextView>(R.id.label_fab_action).text =
				labels[actions.indexOf(action)]
			val settings = getPref(prefs, action)!!
			val left = vi.findViewById<RadioButton>(R.id.button_fab_left)
			val right = vi.findViewById<RadioButton>(R.id.button_fab_right)
			val show = vi.findViewById<CheckBox>(R.id.checkbox_fab_show)
			left.setOnCheckedChangeListener(null)
			right.setOnCheckedChangeListener(null)
			show.setOnCheckedChangeListener(null)
			left.isChecked = settings.first
			right.isChecked = settings.second
			show.isChecked = settings.third
			left.setOnCheckedChangeListener { _, isChecked ->
				setPref(prefs, action, isChecked, settings.second, settings.third)
				for (otherAction in actions) {
					if (action == otherAction) {
						continue
					}
					val o = getPref(prefs, otherAction)!!
					if (o.first) {
						setPref(prefs, otherAction, false, o.second, o.third)
					}
				}
				(list.adapter as ListAdapter<*>).notifyDataSetChanged()
			}
			right.setOnCheckedChangeListener { _, isChecked ->
				setPref(prefs, action, settings.first, isChecked, settings.third)
				for (otherAction in actions) {
					if (action == otherAction) {
						continue
					}
					val o = getPref(prefs, otherAction)!!
					if (o.second) {
						setPref(prefs, otherAction, o.first, false, o.third)
					}
				}
				(list.adapter as ListAdapter<*>).notifyDataSetChanged()
			}
			show.setOnCheckedChangeListener { _, isChecked ->
				setPref(prefs, action, settings.first, settings.second, isChecked)
			}
			return@ListAdapter vi
		}
	}

	fun getPref(
		prefs: SharedPreferences,
		action: String
	): Triple<Boolean, Boolean, Boolean>? {
		val left = if (prefs.contains("fab_${action}_left")) {
			prefs.getBoolean("fab_${action}_left", false)
		} else {
			null
		}
		val right = if (prefs.contains("fab_${action}_right")) {
			prefs.getBoolean("fab_${action}_right", false)
		} else {
			null
		}
		val show = if (prefs.contains("fab_${action}_show")) {
			prefs.getBoolean("fab_${action}_show", false)
		} else {
			null
		}
		return if (left != null) {
			Triple(left, right!!, show!!)
		} else {
			null
		}
	}

	private fun setPref(
		prefs: SharedPreferences,
		action: String,
		left: Boolean,
		right: Boolean,
		show: Boolean
	) {
		prefs.edit()
			.putBoolean("fab_${action}_left", left)
			.putBoolean("fab_${action}_right", right)
			.putBoolean("fab_${action}_show", show)
			.apply()
	}

	fun getLeftAction(prefs: SharedPreferences): String? {
		for (action in actions) {
			if (prefs.getBoolean("fab_${action}_left", false)) {
				return action
			}
		}
		return null
	}

	fun getRightAction(prefs: SharedPreferences): String? {
		for (action in actions) {
			if (prefs.getBoolean("fab_${action}_right", false)) {
				return action
			}
		}
		return null
	}

	fun getIcon(action: String?): Int {
		if (action == null) {
			return R.drawable.bx_play
		}
		when (action) {
			"showNoteTree" -> {
				return R.drawable.bx_align_left
			}

			"jumpToNote" -> {
				return R.drawable.bx_send
			}

			"editNote" -> {
				return R.drawable.bx_edit_alt
			}

			"shareNote" -> {
				return R.drawable.bx_share_alt
			}

			"deleteNote" -> {
				return R.drawable.bx_trash
			}

			"noteMap" -> {
				return R.drawable.bx_graphql
			}

			"sync" -> {
				return R.drawable.bx_refresh
			}

			else -> {
				return R.drawable.bx_play
			}
		}
	}
}
