package eu.fliegendewurst.triliumdroid.dialog

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import eu.fliegendewurst.triliumdroid.Cache
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.Relation


object ModifyRelationsDialog {
	fun showDialog(activity: MainActivity, currentNote: Note) {
		val dialog = AlertDialog.Builder(activity)
			.setTitle(R.string.dialog_modify_relations)
			.setView(R.layout.dialog_modify_relations)
			.create()
		dialog.show()

		val changes = mutableMapOf<String, Note?>()

		val ownedAttributesList = dialog.findViewById<ListView>(R.id.list_relations)!!
		val ownedAttributes =
			currentNote.getRelations().filter { x -> !x.inherited && !x.templated }.toMutableList()

		dialog.findViewById<Button>(R.id.button_add_relation)!!.setOnClickListener {
			AskForNameDialog.showDialog(activity, R.string.dialog_add_relation, "") {
				JumpToNoteDialog.showDialogReturningNote(
					activity,
					R.string.dialog_select_note
				) { targetNote ->
					val note = Cache.getNote(targetNote.note)!!
					changes[it.trim()] = note
					ownedAttributes.add(
						Relation(
							note,
							it.trim(),
							inheritable = false,
							promoted = true,
							multi = false
						)
					)
					(ownedAttributesList.adapter as BaseAdapter).notifyDataSetChanged()
				}
			}
		}

		// add template labels
		for (template in currentNote.getLabels().filter { x -> x.name.startsWith("relation:") }) {
			val labelName = template.name.removePrefix("relation:").removeSuffix("(inheritable)")
			val inheritable = template.name.endsWith("(inheritable)")
			if (ownedAttributes.any { x -> x.name == labelName }) {
				continue
			}
			val settings = template.value.split(',')
			ownedAttributes.add(
				Relation(
					null,
					labelName,
					inheritable,
					settings.contains("promoted"),
					!settings.contains("single")
				)
			)
		}
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
						R.layout.item_relation_input,
						ownedAttributesList,
						false
					)
				}
				vi!!.findViewById<TextView>(R.id.label_relation_name).text = attribute.name
				val button = vi.findViewById<Button>(R.id.button_relation_target)
				if (changes.containsKey(attribute.name)) {
					button.text = changes[attribute.name]?.title ?: "none"
				} else {
					button.text = attribute.target?.title ?: "none"
				}
				button.setOnClickListener {
					JumpToNoteDialog.showDialogReturningNote(
						activity,
						R.string.dialog_select_note
					) { targetNote ->
						changes[attribute.name] = Cache.getNote(targetNote.note)!!
						(ownedAttributesList.adapter as BaseAdapter).notifyDataSetChanged()
					}
				}
				vi.findViewById<Button>(R.id.button_delete_relation).setOnClickListener {
					changes[attribute.name] = null
					ownedAttributes.removeAt(position)
					(ownedAttributesList.adapter as BaseAdapter).notifyDataSetChanged()
				}
				return vi
			}
		}

		dialog.findViewById<Button>(R.id.button_modify_relations)!!.setOnClickListener {
			done(activity, dialog, changes, currentNote)
		}
	}

	private fun done(
		activity: MainActivity,
		dialog: AlertDialog,
		changes: Map<String, Note?>,
		currentNote: Note
	) {
		val previousLabels = currentNote.getRelations().map {
			return@map Pair(it.name, it.target)
		}.toMap()
		android.util.Log.d("modify", changes.toString())
		for (change in changes) {
			val attrName = change.key
			val attrValue = change.value
			if (attrValue == null) {
				Cache.deleteRelation(currentNote, attrName)
				continue
			}
			if (previousLabels[attrName] == attrValue) {
				continue
			}
			val inheritable =
				currentNote.getRelations().filter { x -> x.name == attrName }
					.map { x -> x.inheritable }.firstOrNull()
					?: false
			Cache.updateRelation(currentNote, attrName, attrValue, inheritable)
		}
		dialog.dismiss()
		activity.refreshWidgets(currentNote)
	}
}