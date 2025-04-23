package eu.fliegendewurst.triliumdroid.fragment.note

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.iterator
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import eu.fliegendewurst.triliumdroid.FrontendBackendApi
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.data.Blob
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.database.Attributes
import eu.fliegendewurst.triliumdroid.database.Notes
import eu.fliegendewurst.triliumdroid.databinding.FragmentCanvasNoteBinding
import eu.fliegendewurst.triliumdroid.fragment.NoteRelatedFragment
import eu.fliegendewurst.triliumdroid.util.MyWebChromeClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


class CanvasNoteFragment : Fragment(R.layout.fragment_canvas_note), NoteRelatedFragment {
	companion object {
		private const val TAG: String = "CanvasNoteFragment"
		const val WEBVIEW_DOMAIN: String = "https://trilium-notes.invalid/"
	}

	private lateinit var binding: FragmentCanvasNoteBinding
	private var handler: Handler? = null
	private var note: Note? = null
	private var blob: Blob? = null
	private var load: Boolean = false
	private var subCodeNotes: List<Note>? = null
	var console: MutableList<ConsoleMessage> = mutableListOf()

	override fun getNoteId() = note?.id

	@SuppressLint("SetJavaScriptEnabled")
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		handler = Handler(requireContext().mainLooper)

		binding = FragmentCanvasNoteBinding.inflate(inflater, container, false)
		binding.webview.settings.javaScriptEnabled = true
		binding.webview.addJavascriptInterface(
			FrontendBackendApi(this, this.requireContext(), handler!!),
			"api"
		)
		binding.webview.webChromeClient = MyWebChromeClient(
			{ (this@CanvasNoteFragment.activity as MainActivity?) },
			{ this@CanvasNoteFragment.console.add(it) })
		val wvc = NoteWebViewClient(
			{ return@NoteWebViewClient note },
			{ return@NoteWebViewClient blob },
			{ return@NoteWebViewClient subCodeNotes },
			{ return@NoteWebViewClient activity as MainActivity? },
			{ startActivity(it) })

		binding.webview.webViewClient = wvc

		if (load) {
			viewLifecycleOwner.lifecycleScope.launch {
				load(note, blob)
			}
		}

		return binding.root
	}

	fun loadLater(note: Note?) {
		load = true
		this.note = note
		this.blob = null
	}

	suspend fun load(noteToLoad: Note?, blobToDisplay: Blob? = null) {
		var note = noteToLoad
		// if called before proper creation
		if (this.activity == null) {
			loadLater(note)
			return
		}
		console.clear()
		subCodeNotes = emptyList()

		this.note = note
		this.blob = blobToDisplay
		this.load = true
		Log.i(TAG, "loading ${note?.id}")
		if (note == null) {
			return
		}
		binding.textId.text = note.id.rawId()
		if (note.content() == null && blob == null) {
			note = Notes.getNoteWithContent(note.id)
		}
		if (note == null) {
			(this@CanvasNoteFragment.activity as MainActivity).handleEmptyNote()
			return
		}
		val consoleLog = false
		var execute = false
		var share = false

		refreshHeader(note)

		binding.webview.loadUrl(WEBVIEW_DOMAIN + note.id.rawId())

		val main = (this@CanvasNoteFragment.activity ?: return) as MainActivity
		// FABs obscure excalidraw menu items
		main.fixVisibilityFABs(true)

		main.setupActions(
			consoleLog,
			execute,
			share,
			note.id == Notes.ROOT
		)
	}

	suspend fun refreshHeader(note: Note) {
		if (!this.load || context == null) {
			return
		}
		if (blob != null) {
			// previous revision: hide labels
			binding.labelNoteRevisionInfo.text = blob!!.dateModified
			binding.noteHeaderAttributes.visibility = View.GONE
			return
		}
		binding.labelNoteRevisionInfo.text = ""
		val constraintLayout = binding.noteHeader
		val flow = binding.noteHeaderAttributes
		val attributeContentDesc = getString(R.string.attribute)
		// remove previously shown attributes
		constraintLayout.iterator().also { iterator ->
			iterator.forEach { view ->
				if (view.contentDescription == attributeContentDesc) {
					iterator.remove()
				}
			}
		}
		for (attribute in note.getLabels()) {
			if (!attribute.promoted) {
				continue
			}
			val view =
				LayoutInflater.from(context)
					.inflate(R.layout.item_attribute, constraintLayout, false)
			view.findViewById<TextView>(R.id.label_attribute_name).text = attribute.name
			val textInput = view.findViewById<TextView>(R.id.label_attribute_value)
			textInput.text = attribute.value()
			textInput.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
				if (hasFocus) {
					return@OnFocusChangeListener
				}
				val newValue = textInput.text
				if (newValue != attribute.value) {
					runBlocking {
						Attributes.updateLabel(
							note,
							attribute.name,
							newValue.toString(),
							attribute.inheritable
						)
					}
				}
			}
			view.layoutParams = ConstraintLayout.LayoutParams(
				ConstraintLayout.LayoutParams.WRAP_CONTENT,
				ConstraintLayout.LayoutParams.WRAP_CONTENT
			)
			view.id = View.generateViewId()
			constraintLayout.addView(view)
			flow.addView(view)
		}
	}
}
