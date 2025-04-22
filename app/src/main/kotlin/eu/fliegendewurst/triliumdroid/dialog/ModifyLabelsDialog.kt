package eu.fliegendewurst.triliumdroid.dialog

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.data.AttributeId
import eu.fliegendewurst.triliumdroid.data.Label
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.database.Attributes
import eu.fliegendewurst.triliumdroid.database.Notes
import eu.fliegendewurst.triliumdroid.service.Util
import kotlinx.coroutines.launch


object ModifyLabelsDialog {
	fun showDialog(activity: MainActivity, currentNote: Note) = activity.lifecycleScope.launch {
		val dialog = AlertDialog.Builder(activity)
			.setTitle(R.string.dialog_modify_labels)
			.setView(R.layout.dialog_modify_labels)
			.create()
		dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
		dialog.show()

		var requestedFocus = false

		val changes = mutableMapOf<String, String?>()
		val changePromoted = mutableMapOf<String, Boolean>()

		val ownedAttributesList = dialog.findViewById<ListView>(R.id.list_labels)!!
		val ownedAttributes =
			currentNote.getLabels().filter { x -> !x.inherited && !x.templated }.toMutableList()
		val watchers = arrayOfNulls<TextWatcher?>(ownedAttributes.size).toMutableList()

		dialog.findViewById<Button>(R.id.button_add_label)!!.setOnClickListener {
			AskForNameDialog.showDialog(activity, R.string.dialog_add_label, "") {
				val name = it.trim()
				ownedAttributes.add(
					Label(
						AttributeId(Util.randomString(12)), // TODO: collisions?
						name,
						"",
						inheritable = false,
						promoted = true,
						multi = false
					)
				)
				watchers.add(null)
				(ownedAttributesList.adapter as BaseAdapter).notifyDataSetChanged()
				changePromoted[name] = true
			}
		}

		// add template labels
		for (template in currentNote.getLabels().filter { x -> x.name.startsWith("label:") }) {
			val labelName = template.name.removePrefix("label:").removeSuffix("(inheritable)")
			val inheritable = template.name.endsWith("(inheritable)")
			if (ownedAttributes.any { x -> x.name == labelName }) {
				continue
			}
			val settings = template.value.split(',')
			ownedAttributes.add(
				Label(
					AttributeId(Util.randomString(12)), // TODO: collisions?
					labelName,
					"",
					inheritable,
					settings.contains("promoted"),
					!settings.contains("single")
				)
			)
		}
		while (watchers.size < ownedAttributes.size) {
			watchers.add(null)
		}
		ownedAttributesList.itemsCanFocus = true
		ownedAttributesList.adapter = object : BaseAdapter() {
			override fun getCount(): Int {
				return ownedAttributes.size
			}

			override fun getItem(position: Int): Any {
				return ownedAttributes[position]
			}

			override fun getItemId(position: Int): Long {
				return position.toLong()
			}

			override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
				val attribute = ownedAttributes[position]
				var vi = convertView
				if (vi == null) {
					vi = activity.layoutInflater.inflate(
						R.layout.item_label_input,
						ownedAttributesList,
						false
					)
				}
				vi!!.findViewById<TextView>(R.id.label_label_title).text = attribute.name
				val content = vi.findViewById<EditText>(R.id.edit_label_content)
				val promotedCheckbox = vi.findViewById<CheckBox>(R.id.checkbox_promoted)
				val deleteButton = vi.findViewById<Button>(R.id.button_delete_label)
				if (watchers[position] != null) {
					content.removeTextChangedListener(watchers[position])
				}
				content.setText(attribute.value())
				promotedCheckbox.setOnCheckedChangeListener(null)
				promotedCheckbox.isChecked = attribute.promoted
				promotedCheckbox.setOnCheckedChangeListener { _, isChecked ->
					changePromoted[attribute.name] = isChecked
				}
				deleteButton.setOnClickListener {
					changes[attribute.name] = null
					ownedAttributes.removeAt(position)
					content.removeTextChangedListener(watchers.removeAt(position))
					(ownedAttributesList.adapter as BaseAdapter).notifyDataSetChanged()
				}
				watchers[position] = object : TextWatcher {
					override fun beforeTextChanged(
						s: CharSequence?,
						start: Int,
						count: Int,
						after: Int
					) {
					}

					override fun onTextChanged(
						s: CharSequence?,
						start: Int,
						before: Int,
						count: Int
					) {
					}

					override fun afterTextChanged(s: Editable?) {
						if (s == null) {
							return
						}
						changes[attribute.name] = s.toString()
					}

				}
				content.addTextChangedListener(watchers[position])
				if (!requestedFocus && attribute.value().isEmpty()) {
					content.requestFocus()
					activity.handler.postDelayed({
						val imm =
							activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
						imm.showSoftInput(content, 0)
					}, 200)
					requestedFocus = true
				}
				return vi
			}
		}

		dialog.findViewById<Button>(R.id.button_modify_labels)!!.setOnClickListener {
			done(activity, dialog, changes, changePromoted, currentNote, ownedAttributes)
		}
	}

	private fun done(
		activity: MainActivity,
		dialog: AlertDialog,
		changes: Map<String, String?>,
		changePromoted: Map<String, Boolean>,
		currentNote: Note,
		labels: List<Label>
	) = activity.lifecycleScope.launch {
		android.util.Log.d("lbl", "changes $changes $changePromoted")
		val previousLabels = currentNote.getLabels().map {
			return@map Pair(it.name, it.value)
		}.toMap()
		for (change in changePromoted) {
			val attrName = change.key
			val promoted = change.value
			Attributes.setPromoted(currentNote, attrName, promoted)
		}
		for (change in changes) {
			val attrName = change.key
			val attrValue = change.value
			if (attrValue == null) {
				Attributes.deleteLabel(currentNote, attrName)
				continue
			}
			if (previousLabels[attrName] == attrValue) {
				continue
			}
			val inheritable =
				labels.filter { x -> x.name == attrName }.map { x -> x.inheritable }
					.firstOrNull()
					?: false
			Attributes.updateLabel(currentNote, attrName, attrValue, inheritable)
		}
		dialog.dismiss()
		currentNote.clearAttributeCache()
		Notes.notes[currentNote.id]?.clearAttributeCache()
		activity.refreshWidgets(currentNote)
	}
}
