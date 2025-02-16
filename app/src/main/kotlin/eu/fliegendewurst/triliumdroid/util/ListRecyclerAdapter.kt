package eu.fliegendewurst.triliumdroid.util

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

/**
 * Use [ListAdapter.submitList] to display items
 */
class ListRecyclerAdapter<T, V: ViewBinding>(
	private val onBind: (V, T) -> Unit,
	private val inflate: (ViewGroup) -> V
) :
	ListAdapter<T, ListRecyclerAdapter.ItemViewHolder<T, V>>(ItemDiffCallback()) {

	class ItemViewHolder<T, V>(
		private val binding: V,
		itemView: View,
		val onBind: (V, T) -> Unit,
	) :
		RecyclerView.ViewHolder(itemView) {
		fun bind(item: T) {
			onBind(binding, item)
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder<T, V> {
		val binding = inflate(parent)
		return ItemViewHolder(binding, binding.root, onBind)
	}

	override fun onBindViewHolder(holder: ItemViewHolder<T, V>, position: Int) {
		val item = getItem(position)
		holder.bind(item)
	}

	private class ItemDiffCallback<T> : DiffUtil.ItemCallback<T>() {
		override fun areItemsTheSame(
			oldItem: T & Any,
			newItem: T & Any
		): Boolean {
			return oldItem == newItem
		}

		@SuppressLint("DiffUtilEquals")
		override fun areContentsTheSame(
			oldItem: T & Any,
			newItem: T & Any
		): Boolean {
			return oldItem == newItem
		}
	}
}
