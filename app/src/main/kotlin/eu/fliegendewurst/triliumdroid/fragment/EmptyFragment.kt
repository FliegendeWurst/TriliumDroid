package eu.fliegendewurst.triliumdroid.fragment

import android.os.Bundle

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.databinding.FragmentEmptyBinding

class EmptyFragment : Fragment(R.layout.fragment_empty) {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		return FragmentEmptyBinding.inflate(inflater, container, false).root
	}
}