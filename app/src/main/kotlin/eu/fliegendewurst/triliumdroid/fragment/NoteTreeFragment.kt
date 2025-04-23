package eu.fliegendewurst.triliumdroid.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.TreeItemAdapter
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.databinding.FragmentNoteTreeBinding

class NoteTreeFragment : Fragment(R.layout.fragment_note_tree) {
	lateinit var binding: FragmentNoteTreeBinding
	private var callbackInit: ((NoteTreeFragment) -> Unit)? = null

	@SuppressLint("NotifyDataSetChanged")
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentNoteTreeBinding.inflate(inflater, container, false)
		if (callbackInit != null) {
			callbackInit!!.invoke(this)
		}
		if (binding.treeList.adapter != null) {
			(binding.treeList.adapter as TreeItemAdapter).notifyDataSetChanged()
		} else {
			binding.treeList.adapter = (requireActivity() as MainActivity).tree
		}
		return binding.root
	}

	fun initLater(callback: (NoteTreeFragment) -> Unit) {
		callbackInit = callback
	}
}
