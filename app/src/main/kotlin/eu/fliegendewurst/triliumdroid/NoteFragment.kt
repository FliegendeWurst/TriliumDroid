package eu.fliegendewurst.triliumdroid

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler

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
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.databinding.FragmentNoteBinding


class NoteFragment : Fragment(R.layout.fragment_note) {
	companion object {
		private const val TAG: String = "NoteFragment"
		private const val WEBVIEW_DOMAIN: String = "https://trilium-notes.invalid/"
		private const val WEBVIEW_HOST: String = "trilium-notes.invalid"
	}

	private lateinit var binding: FragmentNoteBinding
	private var handler: Handler? = null
	private var id: String = ""
	private var load: Boolean = false
	private var subCodeNotes: List<Note>? = null
	var console: MutableList<ConsoleMessage> = mutableListOf()

	fun getNoteId(): String {
		return id
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
				(this@NoteFragment.activity as MainActivity).enableConsoleLogAction()
				this@NoteFragment.console.add(consoleMessage ?: return true)
				return true
			}
		}
		binding.webview.webViewClient = object : WebViewClient() {
			override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
				// called when an internal link is used
				if (url.startsWith(WEBVIEW_DOMAIN) && url.contains('#')) {
					val parts = url.split('/')
					val lastPart = parts.last()
					if (lastPart == id) {
						return
					}
					val id = parts.last()
					Log.i(TAG, "navigating to note $id")
					(activity as MainActivity).navigateTo(Cache.getNote(id)!!)
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
				Log.d(
					TAG,
					"intercept: ${request.url.host} ${request.url.query} ${request.url} ${request.url.pathSegments.size}"
				)
				if (request.url.host == WEBVIEW_HOST && request.url.pathSegments.size == 1) {
					val id = request.url.lastPathSegment!!
					if (id == "favicon.ico") {
						return null // TODO: catch all invalid IDs
					}
					val note = Cache.getNoteWithContent(id)
					return if (note != null) {
						var data = note.content!!
						if (note.id == this@NoteFragment.id && subCodeNotes != null && !note.contentFixed) {
							// append <script> tags to load children
							if (data.isEmpty()) {
								data += "<!DOCTYPE html>".encodeToByteArray()
							}
							for (n in subCodeNotes!!) {
								data += "<script src='/${n.id}'></script>".encodeToByteArray()
							}
							note.content = data
							note.contentFixed = true
						}
						WebResourceResponse(note.mime, null, data.inputStream())
					} else {
						null
					}
				}
				return null
			}
		}

		if (load) {
			load(id)
		}

		return binding.root
	}

	@SuppressLint("MissingInflatedId")
	fun load(id: String) {
		console.clear()
		subCodeNotes = emptyList()

		this.id = id
		this.load = true
		Log.i(TAG, "loading $id")
		binding.textId.text = id
		val note = Cache.getNoteWithContent(id) ?: return
		handler!!.post {
			val consoleLog = false
			var execute = false
			var share = false

			val constraintLayout = binding.noteHeader
			val flow = binding.noteHeaderAttributes
			// remove previously shown attributes
			constraintLayout.iterator().also { iterator ->
				iterator.forEach { view ->
					if (view.contentDescription == "Label") {
						iterator.remove()
					}
				}
			}
			for (attribute in note.getAttributes()) {
				if (!attribute.promoted) {
					continue
				}
				val view =
					LayoutInflater.from(context)
						.inflate(R.layout.item_attribute, constraintLayout, false)
				view.findViewById<TextView>(R.id.label_attribute_name).text = attribute.name
				view.findViewById<TextView>(R.id.label_attribute_value).text = attribute.value()
				view.layoutParams = ConstraintLayout.LayoutParams(
					ConstraintLayout.LayoutParams.WRAP_CONTENT,
					ConstraintLayout.LayoutParams.WRAP_CONTENT
				)
				view.id = View.generateViewId()
				constraintLayout.addView(view)
				flow.addView(view)
			}

			if (note.mime.contains("env=frontend")) {
				execute = true
			}
			if (note.content?.size?.compareTo(0).let { (it ?: 0) > 0 } && arrayOf(
					"text",
					"code",
					"image"
				).contains(note.type)) {
				share = true
			}

			if (note.type == "render") {
				val renderTarget = note.getRelation("renderNote") ?: return@post
				load(renderTarget.id)
			} else if (note.type == "code") {
				// code notes automatically load all the scripts in child nodes
				// -> modify content returned by webview interceptor
				subCodeNotes = note.children.orEmpty().map { Cache.getNote(it.value.note)!! }
				binding.webview.loadUrl(WEBVIEW_DOMAIN + id)
			} else if (note.mime.startsWith("text/") || note.mime.startsWith("image/svg")) {
				binding.webview.loadUrl(WEBVIEW_DOMAIN + id)
			} else {
				binding.webview.settings.builtInZoomControls = true
				binding.webview.settings.displayZoomControls = false
				binding.webview.loadDataWithBaseURL(
					WEBVIEW_DOMAIN,
					"<meta name='viewport' content='width=device-width, initial-scale=1'><img style='max-width: 100%' src='/$id'>",
					"text/html; charset=UTF-8",
					"UTF-8",
					null
				)
			}

			(this@NoteFragment.activity as MainActivity).setupActions(consoleLog, execute, share)
		}
	}
}