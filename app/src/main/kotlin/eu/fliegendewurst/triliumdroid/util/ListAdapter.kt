package eu.fliegendewurst.triliumdroid.util

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter

class ListAdapter<T>(private val values: List<T>, private val getView: (T, View?) -> View) :
	BaseAdapter() {
	override fun getCount(): Int {
		return values.size
	}

	override fun getItem(position: Int): Any {
		return values[position] as Any
	}

	override fun getItemId(position: Int): Long {
		return position.toLong()
	}

	override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
		return getView.invoke(values[position], convertView)
	}
}
