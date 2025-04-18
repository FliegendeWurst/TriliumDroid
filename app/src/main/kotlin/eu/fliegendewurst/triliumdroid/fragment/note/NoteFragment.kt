package eu.fliegendewurst.triliumdroid.fragment.note

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
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
import eu.fliegendewurst.triliumdroid.util.Assets
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


class NoteFragment : Fragment(R.layout.fragment_note), NoteRelatedFragment {
	companion object {
		private const val TAG: String = "NoteFragment"
		private const val WEBVIEW_DOMAIN: String = "https://trilium-notes.invalid/"
		private const val WEBVIEW_HOST: String = "trilium-notes.invalid"
		private val FIXER: Regex =
			"\\s*(<div>|<div class=\"[^\"]+\">)(.*)</div>\\s*".toRegex(RegexOption.DOT_MATCHES_ALL)
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
		binding.webview.webChromeClient = object : WebChromeClient() {
			override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
				if (consoleMessage == null) {
					return true /* handled */
				}
				Log.d(TAG, "console.log ${consoleMessage.message()}")
				(this@NoteFragment.activity as MainActivity).enableConsoleLogAction()
				this@NoteFragment.console.add(consoleMessage)
				return true
			}
		}
		binding.webview.webViewClient = object : WebViewClient() {
			override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
				// called when an internal link is used
				if (url.startsWith(WEBVIEW_DOMAIN) && url.contains('#')) {
					val parts = url.split('/')
					val lastPart = parts.last()
					if (lastPart == note?.id) {
						return
					}
					var id = parts.last().trimStart('#')
					if (id.contains('#')) {
						id = id.split('#').last()
					}
					Log.i(TAG, "navigating to note $id")
					val main = activity as MainActivity? ?: return
					main.lifecycleScope.launch {
						main.navigateTo(Notes.getNote(id) ?: return@launch)
					}
				}
			}

			override fun shouldOverrideUrlLoading(
				view: WebView?,
				request: WebResourceRequest?
			): Boolean {
				// open external sites in external browser
				return if (request?.url?.host != WEBVIEW_HOST) {
					val intent = Intent(Intent.ACTION_VIEW, request!!.url)
					startActivity(intent)
					true
				} else {
					false
				}
			}

			override fun shouldInterceptRequest(
				view: WebView,
				request: WebResourceRequest
			): WebResourceResponse? {
				Log.d(TAG, "intercept: ${request.url.host} ${request.url}")
				if (request.url.host == "esm.sh") {
					val effectiveUrl = request.url.toString()
					val asset = Assets.webAsset(view.context, effectiveUrl)
					val mime = if (effectiveUrl.endsWith(".css")) {
						"text/css"
					} else if (effectiveUrl.endsWith(".woff2")) {
						"font/woff2"
					} else {
						"application/javascript"
					}
					return if (asset != null) {
						val resp = WebResourceResponse(mime, "utf-8", asset)
						resp.responseHeaders = mapOf(Pair("Access-Control-Allow-Origin", "*"))
						resp
					} else {
						Log.e(TAG, "intercept missing: ${request.url} !")
						// catch-all blocker
						WebResourceResponse(
							mime,
							null,
							"esm.sh req. @ $effectiveUrl not cached, please report to TriliumDroid issue tracker".byteInputStream()
						)
					}
				} else if (request.url.host == WEBVIEW_HOST && (request.url.pathSegments.size <= 2 || request.url.pathSegments.size == 5)) {
					// /api/attachments/note_id/image/Trilium%20Demo_trilium-icon.png
					var id = when (request.url.pathSegments.size) {
						0 -> {
							request.url.fragment.orEmpty()
						}

						1 -> {
							request.url.lastPathSegment!!
						}

						// request of /excalidraw-data/${noteId}
						2 -> {
							request.url.pathSegments[1]
						}

						else -> {
							request.url.pathSegments[2]
						}
					}
					if (id == "") {
						id = "root"
					}
					if (id == "favicon.ico") {
						return null // TODO: catch all invalid IDs
					}
					if (id == "excalidraw_loader.js") {
						return WebResourceResponse(
							"text/javascript",
							null,
							Assets.excalidrawLoaderJS(view.context).byteInputStream()
						)
					}
					val note = runBlocking { Notes.getNoteWithContent(id) }
					// viewing revision: override response for main note
					var content = if (blob != null && id == this@NoteFragment.note?.id) {
						blob!!.content
					} else {
						note?.content()
					}
					if (request.url.pathSegments.getOrNull(0) == "excalidraw-data") {
						if (content == null) {
							Log.w(TAG, "canvas note without content")
							return WebResourceResponse(
								"application/json",
								null,
								"{}".byteInputStream()
							)
						}
						return WebResourceResponse("application/json", null, content.inputStream())
					}
					var mime = note?.mime
					if (note == null) {
						// try attachment
						val attachment = runBlocking { Cache.getAttachmentWithContent(id) }
						content = attachment?.content
						mime = attachment?.mime
					}
					if (content == null) {
						return null
					}
					var data: ByteArray = content
					// Excalidraw/Canvas notes: use generic wrapper note
					if (note?.type == "canvas") {
						Log.d(TAG, "canvas note, returning excalidraw template!")
						return WebResourceResponse(
							"text/html",
							"utf-8",
							Assets.excalidrawTemplateHTML(view.context).byteInputStream()
						)
					}
					if (mime == "text/html") {
						// fixup useless nested divs
						while (true) {
							val contentFixed = FIXER.matchEntire(data.decodeToString())
							if (contentFixed != null) {
								data = contentFixed.groups[2]!!.value.encodeToByteArray()
							} else {
								break
							}
						}
					}
					if (note != null && note.id == this@NoteFragment.note?.id && subCodeNotes != null) {
						// append <script> tags to load children
						if (data.isEmpty()) {
							data += "<!DOCTYPE html>".encodeToByteArray()
						}
						for (n in subCodeNotes!!) {
							data += "<script src='/${n.id}'></script>".encodeToByteArray()
						}
					}
					if (mime == "text/html") {
						data += "<style>@media (prefers-color-scheme: dark) { * { color: white; background-color: black; } }</style>".encodeToByteArray()
					}
					return WebResourceResponse(mime, null, data.inputStream())
				}
				return WebResourceResponse(
					"text/plain",
					null,
					"arbitrary internet access not allowed in TriliumDroid sandbox".byteInputStream()
				)
			}
		}

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
		viewLifecycleOwner.lifecycleScope.launch {
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
				val renderTarget = note.getRelation("renderNote") ?: return@launch
				load(renderTarget)
			} else if (note.type == "code") {
				// code notes automatically load all the scripts in child nodes
				// -> modify content returned by webview interceptor
				subCodeNotes =
					note.children.orEmpty().map { Notes.getNote(it.note)!! }
				binding.webview.loadUrl(WEBVIEW_DOMAIN + note.id)
			} else if (note.mime.startsWith("text/") || note.mime.startsWith("image/svg") || note.type == "canvas") {
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

			val main = (this@NoteFragment.activity ?: return@launch) as MainActivity
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

			if (note.content() == null || note.content()?.size == 0 || (
						note.content()?.size == 15 && note.content()
							?.decodeToString() == "<!DOCTYPE html>")
			) {
				main.handleEmptyNote()
			}
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
