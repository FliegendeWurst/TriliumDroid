package eu.fliegendewurst.triliumdroid.dialog

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import eu.fliegendewurst.triliumdroid.Cache
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.Relation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


object ModifyRelationsDialog {
	fun showDialog(activity: MainActivity, currentNote: Note) = activity.lifecycleScope.launch {
		val dialog = AlertDialog.Builder(activity)
			.setTitle(R.string.dialog_modify_relations)
			.setView(R.layout.dialog_modify_relations)
			.create()
		dialog.show()

		// list: attribute name, ID (null if new), target note id (null if deleted)
		val changes = mutableListOf<Triple<String, String?, Note?>>()

		val ownedAttributesList = dialog.findViewById<ListView>(R.id.list_relations)!!
		val ownedAttributes =
			currentNote.getRelations().filter { x -> !x.inherited && !x.templated }.toMutableList()

		dialog.findViewById<Button>(R.id.button_add_relation)!!.setOnClickListener {
			AskForNameDialog.showDialog(activity, R.string.dialog_add_relation, "") {
				JumpToNoteDialog.showDialogReturningNote(
					activity,
					R.string.dialog_select_note
				) { targetNote ->
					val note = runBlocking { Cache.getNote(targetNote.note)!! }
					changes.add(Triple(it.trim(), null, note))
					ownedAttributes.add(
						Relation(
							null,
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
		for (template in currentNote.getLabels()
			.filter { x -> x.name.startsWith("relation:") }) {
			val labelName =
				template.name.removePrefix("relation:").removeSuffix("(inheritable)")
			val inheritable = template.name.endsWith("(inheritable)")
			if (ownedAttributes.any { x -> x.name == labelName }) {
				continue
			}
			val settings = template.value.split(',')
			ownedAttributes.add(
				Relation(
					null,
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
				val prevChange =
					changes.find { x -> x.first == attribute.name && x.second == attribute.id }
				if (prevChange != null) {
					button.text = prevChange.third?.title ?: "none"
				} else {
					button.text = attribute.target?.title ?: "none"
				}
				button.setOnClickListener {
					JumpToNoteDialog.showDialogReturningNote(
						activity,
						R.string.dialog_select_note
					) { targetNote ->
						changes.removeIf { x ->
							x.first == attribute.name && x.second == attribute.id && x.third == attribute.target
						}
						changes.add(
							Triple(
								attribute.name,
								attribute.id,
								runBlocking { Cache.getNote(targetNote.note)!! }
							)
						)
						(ownedAttributesList.adapter as BaseAdapter).notifyDataSetChanged()
					}
				}
				vi.findViewById<Button>(R.id.button_delete_relation).setOnClickListener {
					changes.removeIf { x ->
						x.first == attribute.name && x.second == attribute.id && x.third == attribute.target
					}
					changes.add(Triple(attribute.name, attribute.id, null))
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
		changes: List<Triple<String, String?, Note?>>,
		currentNote: Note
	) {
		activity.lifecycleScope.launch {
			val previousLabels = currentNote.getRelations().map {
				return@map Pair(it.id, it.target)
			}.toMap()
			for (change in changes) {
				val attrName = change.first
				val attrId = change.second
				val attrValue = change.third
				if (attrValue == null) {
					Cache.deleteRelation(currentNote, attrName, attrId!!)
					continue
				}
				if (previousLabels[attrId] == attrValue) {
					continue
				}
				val inheritable =
					currentNote.getRelations().filter { x -> x.name == attrName }
						.map { x -> x.inheritable }.firstOrNull()
						?: false
				Cache.updateRelation(currentNote, attrId, attrName, attrValue, inheritable)
			}
			dialog.dismiss()
			activity.refreshWidgets(currentNote)
		}
	}
}
