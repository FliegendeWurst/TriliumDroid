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
import eu.fliegendewurst.triliumdroid.database.Cache
import eu.fliegendewurst.triliumdroid.database.Notes
import eu.fliegendewurst.triliumdroid.databinding.FragmentNoteBinding
import eu.fliegendewurst.triliumdroid.fragment.NoteRelatedFragment
import eu.fliegendewurst.triliumdroid.util.MyWebChromeClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


class NoteFragment : Fragment(R.layout.fragment_note), NoteRelatedFragment {
	companion object {
		private const val TAG: String = "NoteFragment"
		const val WEBVIEW_DOMAIN: String = "https://trilium-notes.invalid/"
		const val WEBVIEW_HOST: String = "trilium-notes.invalid"
	}

	private lateinit var binding: FragmentNoteBinding
	private var handler: Handler? = null
	private var note: Note? = null
	private var blob: Blob? = null
	private var load: Boolean = false
	private var subCodeNotes: List<Note>? = null
	var console: MutableList<ConsoleMessage> = mutableListOf()

	override fun getNoteId(): String? {
		return note?.id
	}

	@SuppressLint("SetJavaScriptEnabled")
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		handler = Handler(requireContext().mainLooper)

		binding = FragmentNoteBinding.inflate(inflater, container, false)
		binding.webview.settings.javaScriptEnabled = true
		binding.webview.addJavascriptInterface(
			FrontendBackendApi(this, this.requireContext(), handler!!),
			"api"
		)
		binding.webview.webChromeClient = MyWebChromeClient(
			{ (this@NoteFragment.activity as MainActivity?) },
			{ this@NoteFragment.console.add(it) })
		val wvc = NoteWebViewClient(
			{ return@NoteWebViewClient note },
			{ return@NoteWebViewClient blob },
			{ return@NoteWebViewClient subCodeNotes },
			{ return@NoteWebViewClient activity as MainActivity? },
			{ startActivity(it) })

		binding.webview.webViewClient = wvc
		binding.webviewChildrenNotes.webViewClient = wvc

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
		binding.textId.text = note.id
		if (note.content() == null && blob == null) {
			Cache.initializeDatabase(requireContext())
			note = Notes.getNoteWithContent(note.id)
		}
		if (note == null) {
			(this@NoteFragment.activity as MainActivity).handleEmptyNote()
			return
		}
		val consoleLog = false
		var execute = false
		var share = false

		refreshHeader(note)

		if (note.mime.contains("env=frontend")) {
			execute = true
		}
		if (note.content()?.size?.compareTo(0).let { (it ?: 0) > 0 } && arrayOf(
				"text",
				"code",
				"image"
			).contains(note.type)) {
			share = true
		}

		if (note.type == "render") {
			val renderTarget = note.getRelation("renderNote") ?: return
			load(renderTarget)
		} else if (note.type == "code") {
			// code notes automatically load all the scripts in child nodes
			// -> modify content returned by webview interceptor
			subCodeNotes =
				note.children.orEmpty().map { Notes.getNote(it.note)!! }
			binding.webview.loadUrl(WEBVIEW_DOMAIN + note.id)
		} else if (note.mime.startsWith("text/") || note.mime.startsWith("image/svg") || note.type == "canvas" || note.type == "book") {
			binding.webview.loadUrl(WEBVIEW_DOMAIN + note.id)
		} else {
			binding.webview.settings.builtInZoomControls = true
			binding.webview.settings.displayZoomControls = false
			binding.webview.loadDataWithBaseURL(
				WEBVIEW_DOMAIN,
				"<meta name='viewport' content='width=device-width, initial-scale=1'><img style='max-width: 100%' src='/${note.id}'>",
				"text/html; charset=UTF-8",
				"UTF-8",
				null
			)
		}

		val main = (this@NoteFragment.activity ?: return) as MainActivity
		// FABs obscure excalidraw menu items
		if (note.type == "canvas") {
			main.fixVisibilityFABs(true)
		}
		main.setupActions(
			consoleLog,
			execute,
			share,
			note.id == "root"
		)

		// set up children view
		val children = note.children.orEmpty()
		if (children.isNotEmpty()) {
			// TODO: fully support book notes
			binding.webviewChildrenNotes.loadUrl("${WEBVIEW_DOMAIN}note-children/${note.id}")
			binding.webviewChildrenNotes.visibility = View.VISIBLE
		} else {
			binding.webviewChildrenNotes.loadUrl("about:blank")
			binding.webviewChildrenNotes.visibility = View.GONE
		}

		if (note.content() == null || note.content()?.size == 0 || (
					note.content()?.size == 15 && note.content()
						?.decodeToString() == "<!DOCTYPE html>")
		) {
			main.handleEmptyNote()
		}
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
