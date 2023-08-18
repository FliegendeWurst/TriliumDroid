package kellerar.triliumdroid

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.allViews
import androidx.core.view.iterator
import androidx.fragment.app.Fragment
import kellerar.triliumdroid.data.Label
import kellerar.triliumdroid.databinding.FragmentNoteBinding


class NoteFragment() : Fragment(R.layout.fragment_note) {
	companion object {
		private const val TAG: String = "NoteFragment"
		private const val WEBVIEW_DOMAIN: String = "https://trilium-notes.invalid/"
		private const val WEBVIEW_HOST: String = "trilium-notes.invalid"
	}

	private var binding: FragmentNoteBinding? = null
	private var handler: Handler? = null
	private var id: String = ""
	private var load: Boolean = false

	constructor(id: String) : this() {
		this.id = id
		this.load = true
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentNoteBinding.inflate(inflater, container, false)
		binding!!.webview.webViewClient = object : WebViewClient() {
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
				Log.i(TAG, url)
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
				Log.d(TAG, "intercept:")
				Log.d(TAG, request.url.host ?: "no host")
				Log.d(TAG, request.url.query ?: "no query")
				Log.d(TAG, request.url.toString())
				Log.d(TAG, request.url.pathSegments.size.toString())
				if (request.url.host == WEBVIEW_HOST && request.url.pathSegments.size == 1) {
					val id = request.url.lastPathSegment!!
					if (id == "favicon.ico") {
						return null // TODO: catch all invalid IDs
					}
					val note = Cache.getNoteWithContent(id)
					return if (note != null) {
						Log.d(TAG, "intercept returns data")
						WebResourceResponse(note.mime, null, note.content!!.inputStream())
					} else {
						null
					}
				}
				return null
			}
		}
		handler = Handler(requireContext().mainLooper)

		if (load) {
			load(id)
		}

		return binding!!.root
	}

	override fun onDestroyView() {
		super.onDestroyView()
		binding = null
	}

	@SuppressLint("MissingInflatedId")
	fun load(id: String) {
		this.id = id
		this.load = true
		Log.i(TAG, "loading $id")
		binding!!.textId.text = id
		val note = Cache.getNoteWithContent(id) ?: return
		handler!!.post {
			val constraintLayout = binding!!.noteHeader
			val flow = binding!!.noteHeaderAttributes
			// remove previously shown attributes
			constraintLayout.iterator().also { iterator ->
				iterator.forEach { view ->
					if (view.contentDescription == "Label") {
						iterator.remove()
					}
				}
			}
			for (attribute in note.labels ?: emptyList()) {
				Log.d(TAG, "adding one attribute")
				val view =
					LayoutInflater.from(context)
						.inflate(R.layout.item_attribute, constraintLayout, false)
				view.findViewById<TextView>(R.id.label_attribute_name).text = attribute.name
				view.findViewById<TextView>(R.id.label_attribute_value).text = attribute.value
				view.layoutParams = ConstraintLayout.LayoutParams(
					ConstraintLayout.LayoutParams.WRAP_CONTENT,
					ConstraintLayout.LayoutParams.WRAP_CONTENT
				)
				view.id = View.generateViewId()
				constraintLayout.addView(view)
				flow.addView(view)
			}

			if (note.mime.startsWith("text/") || note.mime.startsWith("image/svg")) {
				Log.i(TAG, "updating content for $id")
				binding!!.webview.loadUrl(WEBVIEW_DOMAIN + id)
			} else {
				Log.i(TAG, "embedding img!") // TODO: figure out how to zoom to fit
				binding!!.webview.settings.builtInZoomControls = true
				binding!!.webview.settings.displayZoomControls = false
				binding!!.webview.loadDataWithBaseURL(
					WEBVIEW_DOMAIN,
					"<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><img src='/$id'>",
					"text/html; charset=UTF-8",
					null,
					null
				)
			}
		}
	}
}