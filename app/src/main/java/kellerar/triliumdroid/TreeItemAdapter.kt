package kellerar.triliumdroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kellerar.triliumdroid.databinding.TreeListItemBinding

class TreeItemAdapter(private val onClick: (Branch) -> Unit) : ListAdapter<Pair<Branch, Int>, TreeItemAdapter.TreeItemViewHolder>(TreeItemDiffCallback) {
	class TreeItemViewHolder(private val binding: TreeListItemBinding, itemView: View, val onClick: (Branch) -> Unit) :
			RecyclerView.ViewHolder(itemView) {
		fun bind(item: Pair<Branch, Int>) {
			binding.label.text = Cache.notes[item.first.note]?.title ?: item.first.note
			val params = binding.label.layoutParams
			if (params is ViewGroup.MarginLayoutParams) {
				params.leftMargin = item.second * 20
			}
			binding.label.setOnClickListener { onClick(item.first) }
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TreeItemViewHolder {
		val binding = TreeListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return TreeItemViewHolder(binding, binding.root, onClick)
	}

	override fun onBindViewHolder(holder: TreeItemViewHolder, position: Int) {
		val item = getItem(position)
		holder.bind(item)
	}
}


object TreeItemDiffCallback : DiffUtil.ItemCallback<Pair<Branch, Int>>() {
	override fun areItemsTheSame(oldItem: Pair<Branch, Int>, newItem: Pair<Branch, Int>): Boolean {
		return oldItem == newItem
	}

	override fun areContentsTheSame(oldItem: Pair<Branch, Int>, newItem: Pair<Branch, Int>): Boolean {
		if (Cache.branchesDirty.contains(newItem.first.id)) {
			Cache.branchesDirty.remove(newItem.first.id)
			return false
		}
		return oldItem.first.id == newItem.first.id
	}
}