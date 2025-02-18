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
	const val NOTE_NAVIGATION: String = "noteNavigation"

	/**
	 * See [eu.fliegendewurst.triliumdroid.activity.main.MainActivity.performAction],
	 * and string array "fabs"
	 */
	val actions = mapOf(
		Pair("showNoteTree", R.id.action_show_note_tree),
		Pair("jumpToNote", R.id.action_jump_to_note),
		Pair(NOTE_NAVIGATION, R.id.action_note_navigation),
		Pair("editNote", R.id.action_edit),
		Pair("shareNote", R.id.action_share),
		Pair("deleteNote", R.id.action_delete),
		Pair("noteMap", R.id.action_note_map),
		Pair("sync", R.id.action_sync)
	)

	fun init(prefs: SharedPreferences) {
		for (action in actions.keys) {
			if (getPref(prefs, action) == null) {
				when (action) {
					"showNoteTree" -> {
						setPref(prefs, action, left = true, right = false, show = false)
					}

					"jumpToNote" -> {
						setPref(prefs, action, left = false, right = false, show = false)
					}

					NOTE_NAVIGATION -> {
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
		list.adapter = ListAdapter(actions.keys.toList()) { action, convertView: View? ->
			var vi = convertView
			if (vi == null) {
				vi = dialog.layoutInflater.inflate(
					R.layout.item_fab_action,
					list,
					false
				)
			}
			vi!!.findViewById<TextView>(R.id.label_fab_action).text =
				labels[actions.keys.indexOf(action)]
			val settings = getPref(prefs, action)!!
			val left = vi.findViewById<RadioButton>(R.id.button_fab_left)
			val right = vi.findViewById<RadioButton>(R.id.button_fab_right)
			val show = vi.findViewById<CheckBox>(R.id.checkbox_fab_show)
			left.setOnCheckedChangeListener(null)
			right.setOnCheckedChangeListener(null)
			show.setOnCheckedChangeListener(null)
			left.isChecked = settings.left
			right.isChecked = settings.right
			show.isChecked = settings.show
			left.setOnCheckedChangeListener { _, isChecked ->
				setPref(prefs, action, isChecked, settings.right, settings.show)
				for (otherAction in actions.keys) {
					if (action == otherAction) {
						continue
					}
					val o = getPref(prefs, otherAction)!!
					if (o.left) {
						setPref(prefs, otherAction, false, o.right, o.show)
					}
				}
				(list.adapter as ListAdapter<*>).notifyDataSetChanged()
			}
			right.setOnCheckedChangeListener { _, isChecked ->
				setPref(prefs, action, settings.left, isChecked, settings.show)
				for (otherAction in actions.keys) {
					if (action == otherAction) {
						continue
					}
					val o = getPref(prefs, otherAction)!!
					if (o.right) {
						setPref(prefs, otherAction, o.left, false, o.show)
					}
				}
				(list.adapter as ListAdapter<*>).notifyDataSetChanged()
			}
			show.setOnCheckedChangeListener { _, isChecked ->
				setPref(prefs, action, settings.left, settings.right, isChecked)
			}
			return@ListAdapter vi
		}
	}

	fun getPref(
		prefs: SharedPreferences,
		action: String
	): ActionPref? {
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
			ActionPref(left, right!!, show!!, actions[action]!!)
		} else {
			null
		}
	}

	data class ActionPref(
		val left: Boolean,
		val right: Boolean,
		val show: Boolean,
		val id: Int,
	)

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
		for (action in actions.keys) {
			if (prefs.getBoolean("fab_${action}_left", false)) {
				return action
			}
		}
		return null
	}

	fun getRightAction(prefs: SharedPreferences): String? {
		for (action in actions.keys) {
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

			NOTE_NAVIGATION -> {
				return R.drawable.bx_globe_alt
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
