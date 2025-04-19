package eu.fliegendewurst.triliumdroid.fragment.note

import android.content.Intent
import android.text.Html.escapeHtml
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.lifecycleScope
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.data.AttachmentId
import eu.fliegendewurst.triliumdroid.data.Blob
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.load
import eu.fliegendewurst.triliumdroid.database.Attachments
import eu.fliegendewurst.triliumdroid.database.Notes
import eu.fliegendewurst.triliumdroid.fragment.note.NoteFragment.Companion.WEBVIEW_DOMAIN
import eu.fliegendewurst.triliumdroid.fragment.note.NoteFragment.Companion.WEBVIEW_HOST
import eu.fliegendewurst.triliumdroid.util.Assets
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class NoteWebViewClient(
	private val note: () -> Note?,
	private val blob: () -> Blob?,
	private val subCodeNotes: () -> List<Note>?,
	private val getActivity: () -> MainActivity?,
	private val startActivity: (Intent) -> Unit
) : WebViewClient() {
	companion object {
		private const val TAG = "NoteWebViewClient"
		private val FIXER: Regex =
			"\\s*(<div>|<div class=\"[^\"]+\">)(.*)</div>\\s*".toRegex(RegexOption.DOT_MATCHES_ALL)
	}

	override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
		// called when an internal link is used
		if (url.startsWith(WEBVIEW_DOMAIN) && url.contains('#')) {
			val parts = url.split('/')
			val lastPart = parts.last()
			if (lastPart == note()?.id) {
				return
			}
			var id = parts.last().trimStart('#')
			if (id.contains('#')) {
				id = id.split('#').last()
			}
			Log.i(TAG, "navigating to note $id")
			val main = getActivity() ?: return
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
					"esm.sh req. @ ${request.url} not cached, please report to TriliumDroid issue tracker".byteInputStream()
				)
			}
		} else if (request.url.host == WEBVIEW_HOST && (request.url.pathSegments.size <= 2 || request.url.pathSegments.size == 5 || request.url.pathSegments.size == 6)) {
			val fetchingAttachment = request.url.pathSegments.size >= 5
			var id = when (request.url.pathSegments.size) {
				0 -> {
					request.url.fragment.orEmpty()
				}

				1 -> {
					request.url.lastPathSegment!!
				}

				// request of /excalidraw-data/${noteId}
				// or /note-children/${noteId}
				2 -> {
					request.url.pathSegments[1]
				}

				// /api/attachments/note_id/image/Trilium%20Demo_trilium-icon.png
				5 -> {
					request.url.pathSegments[2]
				}

				6 -> {
					// /note-children/api/attachments/ID
					request.url.pathSegments[3]
				}

				else -> {
					throw IllegalStateException("wrong number of path segments")
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
			val blobToShow = blob()
			var content = if (blobToShow != null && id == note()?.id) {
				blobToShow.content
			} else {
				note?.content()
			}
			val firstSegment = request.url.pathSegments.getOrNull(0)
			if (firstSegment == "excalidraw-data" && !fetchingAttachment) {
				if (content == null) {
					Log.w(TAG, "canvas note without content")
					return WebResourceResponse(
						"application/json",
						null,
						"{}".byteInputStream()
					)
				}
				return WebResourceResponse("application/json", null, content.inputStream())
			} else if (firstSegment == "note-children" && !fetchingAttachment) {
				return runBlocking {
					val children = note!!.computeChildren()
					var html = Assets.noteChildrenTemplateHTML(view.context)
					html += "<div class='note-list-container'>"
					for (child in children) {
						val contentNote = Notes.getNoteWithContent(child.note) ?: continue
						val contentDecoded = if (contentNote.type != "text") {
							"${contentNote.type} note"
						} else {
							contentNote.content()?.decodeToString() ?: ""
						}
						html += "<div class='note-book-card'>"
						html += "<h5 class='note-book-header'><a href='#${child.note}'>"
						html += escapeHtml(contentNote.title())
						html += "</a></h5>"
						html += "<div class='note-book-content type-text'>"
						html += contentDecoded
						html += "</div>"
						html += "</div>"
					}
					html += "</div>"
					return@runBlocking WebResourceResponse(
						"text/html",
						"utf-8",
						html.byteInputStream()
					)
				}
			}
			var mime = note?.mime
			if (note == null) {
				// try attachment
				val attachment = runBlocking { Attachments.load(AttachmentId(id)) }
				content = runBlocking { attachment?.blobId?.load()?.content }
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
			val subCodeNotesToRun = subCodeNotes()
			if (note != null && note.id == note()?.id && subCodeNotesToRun != null) {
				// append <script> tags to load children
				if (data.isEmpty()) {
					data += "<!DOCTYPE html>".encodeToByteArray()
				}
				for (n in subCodeNotesToRun) {
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
