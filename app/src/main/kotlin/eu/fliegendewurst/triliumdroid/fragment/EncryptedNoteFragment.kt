package eu.fliegendewurst.triliumdroid.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.databinding.FragmentEncryptedNoteBinding
import eu.fliegendewurst.triliumdroid.service.ProtectedSession


class EncryptedNoteFragment : Fragment(R.layout.fragment_encrypted_note) {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		val binding = FragmentEncryptedNoteBinding.inflate(inflater, container, false)

		binding.buttonEnterProtectedSession.setOnClickListener {
			val error = ProtectedSession.enter()
			if (error != null) {
				Toast.makeText(activity, error, Toast.LENGTH_SHORT).show()
			} else {
				val main = activity as MainActivity?
				main?.reloadNote()
			}
		}

		return binding.root
	}
}
