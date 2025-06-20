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
import eu.fliegendewurst.triliumdroid.data.NoteId
import eu.fliegendewurst.triliumdroid.database.Attachments
import eu.fliegendewurst.triliumdroid.database.Cache
import eu.fliegendewurst.triliumdroid.database.Notes
import eu.fliegendewurst.triliumdroid.fragment.note.NoteFragment.Companion.WEBVIEW_DOMAIN
import eu.fliegendewurst.triliumdroid.fragment.note.NoteFragment.Companion.WEBVIEW_HOST
import eu.fliegendewurst.triliumdroid.util.Assets
import eu.fliegendewurst.triliumdroid.util.Preferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

class NoteWebViewClient(
	private val note: () -> Note?,
	private val blob: () -> Blob?,
	private val subCodeNotes: () -> List<Note>?,
	private val getActivity: () -> MainActivity?,
	private val startActivity: (Intent) -> Unit
) : WebViewClient() {
	companion object {
		private const val TAG = "NoteWebViewClient"
	}

	override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
		// called when an internal link is used
		if (url.startsWith(WEBVIEW_DOMAIN) && url.contains('#') && !url.contains("/note-editable#")) {
			val parts = url.split('/')
			val lastPart = parts.last()
			if (lastPart == note()?.id?.rawId()) {
				return
			}
			var id = parts.last().trimStart('#')
			if (id.contains('#')) {
				id = id.split('#').last()
			}
			Log.i(TAG, "navigating to note $id")
			val main = getActivity() ?: return
			main.lifecycleScope.launch {
				main.navigateTo(Notes.getNote(NoteId(id)) ?: return@launch)
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
		val context = view.context
		if (request.url.host == "tile.openstreetmap.org") {
			return null // for geomap notes
		}
//		Log.d(TAG, "intercept: ${request.url.host} ${request.url}")
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
					"utf-8",
					"esm.sh req. @ ${request.url} not cached, please report to TriliumDroid issue tracker".byteInputStream()
				)
			}
		} else if (request.url.host == WEBVIEW_HOST) {
			val firstSegment = request.url.pathSegments.getOrNull(0)
			// special case /stylesheets
			if (firstSegment == "stylesheets") {
				val asset = Assets.stylesheet(
					context,
					request.url.pathSegments.slice(1 until request.url.pathSegments.size)
						.joinToString("/")
				)
				if (asset == null) {
					Log.e(TAG, "failed to find stylesheet!")
					return notFound("text/css")
				}
				return WebResourceResponse("text/css", "utf-8", asset)
			}
			val fetchingAttachment = request.url.pathSegments.size >= 5
			var id = when (request.url.pathSegments.size) {
				0 -> {
					request.url.fragment.orEmpty()
				}

				// or /note-editable#${noteId}
				1 -> {
					request.url.lastPathSegment!!
				}

				// request of /excalidraw-data/${noteId}
				// or /note-children/${noteId}
				// or /note-raw/${noteId}
				2 -> {
					request.url.pathSegments[1]
				}

				// /api/attachments/attachment_id/image/Trilium%20Demo_trilium-icon.png
				5 -> {
					request.url.pathSegments[2]
				}

				6 -> {
					// /note-children/api/attachments/ID
					request.url.pathSegments[3]
				}

				else -> {
					return notFound("text/plain")
				}
			}
			if (id == "") {
				id = "root"
			}
			if (id == "favicon.ico") {
				return notFound("image/x-icon")
			}
			if (id == "ckeditor.js") {
				return WebResourceResponse(
					"text/javascript",
					"utf-8",
					Assets.ckeditorJS(view.context).byteInputStream()
				)
			}
			if (id == "excalidraw_loader.js") {
				return WebResourceResponse(
					"text/javascript",
					"utf-8",
					Assets.excalidrawLoaderJS(view.context).byteInputStream()
				)
			}
			if (id == "geomap_loader.js") {
				return WebResourceResponse(
					"text/javascript",
					"utf-8",
					Assets.geomapLoaderJS(view.context).byteInputStream()
				)
			}
			if (id == "note-editable") {
				Log.d(TAG, "returning note editable template")
				return WebResourceResponse(
					"text/html",
					"utf-8",
					Assets.noteEditableTemplateHTML(view.context).byteInputStream()
				)
			}
			if (id == "noteEditable.js") {
				return WebResourceResponse(
					"text/javascript",
					"utf-8",
					Assets.noteEditableJS(view.context).byteInputStream()
				)
			}
			val note = if (!fetchingAttachment) {
				runBlocking { Notes.getNoteWithContent(NoteId(id)) }
			} else {
				null
			}
			val attachment = if (fetchingAttachment) {
				runBlocking { Attachments.load(AttachmentId(id)) }
			} else {
				null
			}
			if (note?.type == "doc") {
				return runBlocking {
					val docName =
						note.getLabel("docName") ?: return@runBlocking notFound("text/html")
					val asset = Assets.docAsset(view.context, docName)
						?: return@runBlocking notFound("text/html")
					return@runBlocking WebResourceResponse(
						"text/html",
						"utf-8",
						asset
					)
				}
			}
			// viewing revision: override response for main note
			val blobToShow = blob()
			var content = if (fetchingAttachment) {
				runBlocking { attachment?.blob() }?.content
			} else if (blobToShow != null && id == note()?.id?.rawId()) {
				blobToShow.content
			} else {
				note?.content()
			}
			if (firstSegment == "excalidraw-data" && !fetchingAttachment) {
				if (content == null) {
					Log.w(TAG, "canvas note without content")
					return WebResourceResponse(
						"application/json",
						"utf-8",
						"{}".byteInputStream()
					)
				}
				val override = Preferences.canvasViewportOverride(NoteId(id))
				if (override != null) {
					Log.d(TAG, "canvas override $override")
					content =
						content + "CANVAS_OVERRIDE{'scrollX':${override.x},'scrollY':${override.y},'zoom':{'value':${override.zoom}}}"
							.replace('\'', '"').encodeToByteArray()
				}
				return WebResourceResponse("application/json", "utf-8", content.inputStream())
			} else if (firstSegment == "note-children" && !fetchingAttachment) {
				return runBlocking {
					val children = note?.computeChildren().orEmpty()
					if (children.isEmpty()) {
						return@runBlocking WebResourceResponse(
							"text/html",
							"utf-8",
							"".byteInputStream()
						)
					}
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
						html += "<h5 class='note-book-header'><a href='#${child.note.rawId()}'>"
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
			} else if (firstSegment == "note-raw") {
				if (note == null || content == null) {
					return notFound(note?.mime ?: "text/html")
				}
				return WebResourceResponse(note.mime, "utf-8", content.inputStream())
			} else if (firstSegment == "geomap-data" && !fetchingAttachment) {
				if (content == null) {
					Log.w(TAG, "geomap note without content")
					return WebResourceResponse(
						"application/json",
						"utf-8",
						"{'view': {'center': {'lat': 50.878638227135724, 'lng': 0 }, 'zoom': 3 }, 'pins': [] }"
							.replace('\'', '"').byteInputStream()
					)
				}
				val theJson = JSONObject(content.decodeToString())
				val pinArray = JSONArray()
				val pins = runBlocking { Cache.getGeoMapPins(NoteId(id)) }
				for (pin in pins) {
					val o = JSONObject()
					o.put("noteId", pin.noteId)
					o.put("title", pin.title)
					o.put("lat", pin.lat)
					o.put("lng", pin.lng)
					o.putOpt("iconClass", pin.iconClass)
					o.putOpt("color", pin.color)
					pinArray.put(o)
				}
				theJson.put("pins", pinArray)
				return WebResourceResponse(
					"application/json",
					"utf-8",
					theJson.toString().byteInputStream()
				)
			}
			val mime = if (!fetchingAttachment) {
				note?.mime
			} else {
				attachment?.mime
			}
			if (content == null) {
				return notFound(mime ?: "text/html")
			}
			var data: ByteArray = content
			// Excalidraw/Canvas notes: use generic wrapper note
			if (note?.type == "canvas") {
				return WebResourceResponse(
					"text/html",
					"utf-8",
					Assets.excalidrawTemplateHTML(view.context).byteInputStream()
				)
			} else if (note?.type == "geoMap") {
				return WebResourceResponse(
					"text/html",
					"utf-8",
					Assets.geomapTemplateHTML(view.context).byteInputStream()
				)
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
				data += "<style>img { max-width: 100% !important; }\n@media (prefers-color-scheme: dark) { * { color: white; background-color: black; } }</style>".encodeToByteArray()
				val textSize = Preferences.textSize()
				if (textSize != -1) {
					data += "<style>body{font-size:${textSize}px;}</style>".encodeToByteArray()
				}
			}
			return WebResourceResponse(mime, "utf-8", data.inputStream())
		}
		return WebResourceResponse(
			"text/plain",
			"utf-8",
			"arbitrary internet access not allowed in TriliumDroid sandbox".byteInputStream()
		)
	}

	private fun notFound(mime: String) = WebResourceResponse(
		mime,
		"utf-8",
		404,
		"not found",
		mapOf(),
		"".byteInputStream()
	)
}
