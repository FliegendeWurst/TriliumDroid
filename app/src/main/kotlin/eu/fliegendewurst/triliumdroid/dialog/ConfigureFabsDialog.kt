package eu.fliegendewurst.triliumdroid.dialog

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
import eu.fliegendewurst.triliumdroid.util.Preferences

object ConfigureFabsDialog {
	const val SHOW_NOTE_TREE: String = "showNoteTree"
	const val JUMP_TO_NOTE: String = "jumpToNote"
	const val NOTE_NAVIGATION: String = "noteNavigation"
	const val EDIT_NOTE: String = "editNote"
	const val SHARE_NOTE: String = "shareNote"
	const val DELETE_NOTE: String = "deleteNote"
	const val NOTE_MAP: String = "noteMap"
	const val SYNC: String = "sync"

	/**
	 * See [eu.fliegendewurst.triliumdroid.activity.main.MainActivity.performAction],
	 * and string array "fabs"
	 */
	val actions = mapOf(
		Pair(SHOW_NOTE_TREE, R.id.action_show_note_tree),
		Pair(JUMP_TO_NOTE, R.id.action_jump_to_note),
		Pair(NOTE_NAVIGATION, R.id.action_note_navigation),
		Pair(EDIT_NOTE, R.id.action_edit),
		Pair(SHARE_NOTE, R.id.action_share),
		Pair(DELETE_NOTE, R.id.action_delete),
		Pair(NOTE_MAP, R.id.action_note_map),
		Pair(SYNC, R.id.action_sync)
	)

	fun init() {
		val haveLeftAction = getLeftAction() != null
		val haveRightAction = getRightAction() != null
		for (action in actions.keys) {
			if (getPref(action) == null) {
				when (action) {
					"showNoteTree" -> {
						setPref(action, left = !haveLeftAction, right = false, show = false)
					}

					"jumpToNote" -> {
						setPref(action, left = false, right = false, show = false)
					}

					NOTE_NAVIGATION -> {
						setPref(action, left = false, right = !haveRightAction, show = false)
					}

					else -> {
						setPref(action, left = false, right = false, show = true)
					}
				}
			}
		}
	}

	fun showDialog(
		activity: SetupActivity,
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
			val settings = getPref(action)!!
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
				setPref(action, isChecked, settings.right, settings.show)
				for (otherAction in actions.keys) {
					if (action == otherAction) {
						continue
					}
					val o = getPref(otherAction)!!
					if (o.left) {
						setPref(otherAction, false, o.right, o.show)
					}
				}
				(list.adapter as ListAdapter<*>).notifyDataSetChanged()
			}
			right.setOnCheckedChangeListener { _, isChecked ->
				setPref(action, settings.left, isChecked, settings.show)
				for (otherAction in actions.keys) {
					if (action == otherAction) {
						continue
					}
					val o = getPref(otherAction)!!
					if (o.right) {
						setPref(otherAction, o.left, false, o.show)
					}
				}
				(list.adapter as ListAdapter<*>).notifyDataSetChanged()
			}
			show.setOnCheckedChangeListener { _, isChecked ->
				setPref(action, settings.left, settings.right, isChecked)
			}
			return@ListAdapter vi
		}
	}

	fun getPref(
		action: String
	): ActionPref? {
		val prefs = Preferences.prefs
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
		action: String,
		left: Boolean,
		right: Boolean,
		show: Boolean
	) {
		Preferences.prefs.edit()
			.putBoolean("fab_${action}_left", left)
			.putBoolean("fab_${action}_right", right)
			.putBoolean("fab_${action}_show", show)
			.apply()
	}

	fun getLeftAction(): String? {
		return actions.keys.firstOrNull { Preferences.isLeftAction(it) }
	}

	fun getRightAction(): String? {
		return actions.keys.firstOrNull { Preferences.isRightAction(it) }
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
