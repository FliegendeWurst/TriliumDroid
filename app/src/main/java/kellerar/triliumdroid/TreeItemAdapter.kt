package kellerar.triliumdroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kellerar.triliumdroid.databinding.TreeListItemBinding


class TreeItemAdapter(private val onClick: (Branch) -> Unit) :
	ListAdapter<Pair<Branch, Int>, TreeItemAdapter.TreeItemViewHolder>(TreeItemDiffCallback) {
	private var selectedNote: String? = null

	fun select(noteId: String) {
		val prev = selectedNote
		selectedNote = noteId
		if (prev != null) {
			val idx = Cache.getBranchPosition(prev)
			if (idx != null) {
				notifyItemChanged(idx)
			}
		}
		notifyItemChanged(Cache.getBranchPosition(noteId) ?: return)
	}

	class TreeItemViewHolder(
		private val binding: TreeListItemBinding,
		itemView: View,
		val onClick: (Branch) -> Unit
	) :
		RecyclerView.ViewHolder(itemView) {
		fun bind(item: Pair<Branch, Int>) {
			binding.label.text = Cache.getNote(item.first.note)?.title ?: item.first.note
			val params = binding.label.layoutParams
			if (params is ViewGroup.MarginLayoutParams) {
				params.leftMargin = item.second * 20
			}
			binding.label.setOnClickListener { onClick(item.first) }
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TreeItemViewHolder {
		val binding =
			TreeListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return TreeItemViewHolder(binding, binding.root, onClick)
	}

	override fun onBindViewHolder(holder: TreeItemViewHolder, position: Int) {
		val item = getItem(position)
		holder.bind(item)
		val button = holder.itemView.findViewById<Button>(R.id.label)
		// make sure entries in the "jump to note" dialog are wide
		if (item.first.id == MainActivity.JUMP_TO_NOTE_ENTRY) {
			button.layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
		}
		// highlight the selected note
		if (item.first.note == selectedNote) {
			button
				.backgroundTintList =
				ContextCompat.getColorStateList(holder.itemView.context, R.color.tree_selected)
		} else {
			button
				.backgroundTintList =
				ContextCompat.getColorStateList(holder.itemView.context, R.color.tree_normal)
		}
	}
}


object TreeItemDiffCallback : DiffUtil.ItemCallback<Pair<Branch, Int>>() {
	override fun areItemsTheSame(oldItem: Pair<Branch, Int>, newItem: Pair<Branch, Int>): Boolean {
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