package eu.fliegendewurst.triliumdroid.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import eu.fliegendewurst.triliumdroid.FrontendBackendApi
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.data.NoteId
import eu.fliegendewurst.triliumdroid.database.Notes
import eu.fliegendewurst.triliumdroid.databinding.FragmentNoteEditBinding
import eu.fliegendewurst.triliumdroid.fragment.note.NoteFragment.Companion.WEBVIEW_DOMAIN
import eu.fliegendewurst.triliumdroid.fragment.note.NoteWebViewClient
import eu.fliegendewurst.triliumdroid.util.MyWebChromeClient
import kotlinx.coroutines.runBlocking


class NoteEditFragment : Fragment(R.layout.fragment_note_edit), NoteRelatedFragment {
	companion object {
		private const val TAG: String = "NoteEditFragment"
	}

	private lateinit var binding: FragmentNoteEditBinding
	private var id: NoteId? = null

	fun executeJS(js: String) {
		binding.webviewEditable.evaluateJavascript(js) { }
	}

	fun loadLater(id: NoteId) {
		this.id = id
	}

	@SuppressLint("SetJavaScriptEnabled")
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentNoteEditBinding.inflate(inflater, container, false)
		id = if (id == null) {
			val rawId = savedInstanceState?.getString("NODE_ID")
			if (rawId != null) {
				NoteId(rawId)
			} else {
				null
			}
		} else {
			id
		}
		binding.webviewEditable.settings.javaScriptEnabled = true
		binding.webviewEditable.addJavascriptInterface(
			FrontendBackendApi(this, this.requireContext(), Handler(requireContext().mainLooper)),
			"api"
		)
		binding.webviewEditable.webChromeClient = MyWebChromeClient(
			{ (this@NoteEditFragment.activity as MainActivity?) },
			{ })
		binding.webviewEditable.webViewClient = NoteWebViewClient({
			runBlocking {
				if (id != null) {
					Notes.getNote(id!!)
				} else {
					null
				}
			}
		}, { null }, { null }, { activity as MainActivity? }, { })
		if (id != null) {
			binding.webviewEditable.loadUrl("${WEBVIEW_DOMAIN}note-editable#${id!!.id}")
		}
		return binding.root
	}

	override fun onStop() {
		binding.webviewEditable.evaluateJavascript("scheduledUpdate()") {}
		super.onStop()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putString("NOTE_ID", id?.id)
	}

	override fun getNoteId() = id
}
