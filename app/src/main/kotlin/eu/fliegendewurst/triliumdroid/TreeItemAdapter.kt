package eu.fliegendewurst.triliumdroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.NoteId
import eu.fliegendewurst.triliumdroid.database.Notes
import eu.fliegendewurst.triliumdroid.databinding.ItemTreeNoteBinding
import eu.fliegendewurst.triliumdroid.service.Icon
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap


/**
 * Adapter for the note tree view.
 */
class TreeItemAdapter(
	private val onClick: (Branch) -> Unit,
	private val onLongClick: (Branch) -> Unit,
) :
	ListAdapter<Pair<Branch, Int>, TreeItemAdapter.TreeItemViewHolder>(TreeItemDiffCallback) {
	private var selectedNote: NoteId? = null

	private val branchPosition: MutableMap<NoteId, Int> = ConcurrentHashMap()

	fun getBranchPosition(noteId: NoteId): Int? = branchPosition[noteId]

	override fun submitList(list: List<Pair<Branch, Int>>?) {
		for ((i, pair) in list.orEmpty().withIndex()) {
			branchPosition[pair.first.note] = i
			pair.first.cachedTreeIndex = i
		}
		super.submitList(list)
	}

	fun select(noteId: NoteId) {
		val prev = selectedNote
		selectedNote = noteId
		if (prev != null) {
			val idx = branchPosition[prev]
			if (idx != null) {
				notifyItemChanged(idx)
			}
		}
		notifyItemChanged(branchPosition[noteId] ?: return)
	}

	class TreeItemViewHolder(
		private val binding: ItemTreeNoteBinding,
		itemView: View,
		val onClick: (Branch) -> Unit,
		val onLongClick: (Branch) -> Unit,
	) :
		RecyclerView.ViewHolder(itemView) {
		fun bind(item: Pair<Branch, Int>) {
			val note = runBlocking { Notes.getNote(item.first.note) }
			val noteIcon = note?.icon()
			var setNoteIcon = false
			if (noteIcon != null && noteIcon != "bx bx-file-blank") {
				binding.noteIcon.text = Icon.getUnicodeCharacter(noteIcon)
				setNoteIcon = true
			} else {
				binding.noteIcon.text = ""
			}
			binding.label.text = note?.title() ?: item.first.note.rawId()
			val params = binding.label.layoutParams
			val params2 = binding.noteIcon.layoutParams
			if (params is ViewGroup.MarginLayoutParams && params2 is ViewGroup.MarginLayoutParams) {
				params.leftMargin = item.second * 20
				if (setNoteIcon) {
					binding.label.updatePadding(left = itemView.resources.getDimensionPixelOffset(R.dimen.section_margin))
				} else {
					// guessed
					binding.label.updatePadding(left = itemView.resources.getDimensionPixelOffset(R.dimen.normal_margin))
				}
				params2.leftMargin = item.second * 20
			}
			binding.label.isLongClickable = true
			binding.label.setOnClickListener { onClick(item.first) }
			binding.label.setOnLongClickListener {
				onLongClick(item.first)
				return@setOnLongClickListener true
			}
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TreeItemViewHolder {
		val binding =
			ItemTreeNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return TreeItemViewHolder(binding, binding.root, onClick, onLongClick)
	}

	override fun onBindViewHolder(holder: TreeItemViewHolder, position: Int) {
		val item = getItem(position)
		holder.bind(item)
		val button = holder.itemView.findViewById<Button>(R.id.label)
		val icon = holder.itemView.findViewById<TextView>(R.id.note_icon)
		// make sure entries in the "jump to note" dialog are wide
		if (item.first.id.rawId() == MainActivity.JUMP_TO_NOTE_ENTRY) {
			button.layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT
			)
		}
		// highlight the selected note
		if (item.first.note == selectedNote) {
			button
				.backgroundTintList =
				ContextCompat.getColorStateList(holder.itemView.context, R.color.tree_selected)
			val c = holder.itemView.resources.getColor(R.color.tree_selected_text)
			button.setTextColor(c)
			icon.setTextColor(c)
		} else {
			button
				.backgroundTintList =
				ContextCompat.getColorStateList(holder.itemView.context, R.color.tree_normal)
			val c = holder.itemView.resources.getColor(R.color.tree_normal_text)
			button.setTextColor(c)
			icon.setTextColor(c)
		}
	}

	object TreeItemDiffCallback : DiffUtil.ItemCallback<Pair<Branch, Int>>() {
		override fun areItemsTheSame(
			oldItem: Pair<Branch, Int>,
			newItem: Pair<Branch, Int>
		): Boolean {
			return oldItem == newItem
		}

		override fun areContentsTheSame(
			oldItem: Pair<Branch, Int>,
			newItem: Pair<Branch, Int>
		): Boolean {
			/* TODO: this is where expanded tree items need to be handled
			if (Cache.branchesDirty.contains(newItem.first.id)) {
				Cache.branchesDirty.remove(newItem.first.id)
				return false
			}
			 */
			return oldItem.first.id == newItem.first.id
		}
	}
}
