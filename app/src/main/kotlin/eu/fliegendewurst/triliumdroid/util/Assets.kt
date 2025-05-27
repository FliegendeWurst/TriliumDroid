package eu.fliegendewurst.triliumdroid.util

import android.content.Context
import android.util.Log
import java.io.InputStream
import java.util.zip.ZipFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.notExists
import kotlin.io.path.outputStream

object Assets {
	private const val TAG: String = "Assets"
	private const val LATEST_WEB_ASSETS = 7 // increment when updating web.zip
	private const val LATEST_DOC_ASSETS = 1 // increment when updating doc_notes.zip
	private const val LATEST_CSS_ASSETS = 1 // increment when updating stylesheets.zip

	private var ckeditorJS: String? = null // ~1.8 MB
	private var excalidraw_TPL: String? = null
	private var geomap_TPL: String? = null
	private var excalidraw_loader: String? = null
	private var geomap_loader: String? = null
	private var noteChildren_TPL: String? = null
	private var noteEditable_TPL: String? = null
	private var noteEditable_JS: String? = null

	fun excalidrawTemplateHTML(context: Context): String {
		if (excalidraw_TPL != null) {
			return excalidraw_TPL!!
		}
		context.resources.assets.open("excalidraw_TPL.html").use { fileInput ->
			fileInput.bufferedReader().use {
				excalidraw_TPL = it.readText()
			}
		}
		return excalidraw_TPL!!
	}

	fun geomapTemplateHTML(context: Context): String {
		if (geomap_TPL != null) {
			return geomap_TPL!!
		}
		context.resources.assets.open("geomap_TPL.html").use { fileInput ->
			fileInput.bufferedReader().use {
				geomap_TPL = it.readText()
			}
		}
		return geomap_TPL!!
	}

	fun excalidrawLoaderJS(context: Context): String {
		if (excalidraw_loader != null) {
			return excalidraw_loader!!
		}
		context.resources.assets.open("excalidraw_loader.js").use { fileInput ->
			fileInput.bufferedReader().use {
				excalidraw_loader = it.readText()
			}
		}
		return excalidraw_loader!!
	}

	fun geomapLoaderJS(context: Context): String {
		if (geomap_loader != null) {
			return geomap_loader!!
		}
		context.resources.assets.open("geomap_loader.js").use { fileInput ->
			fileInput.bufferedReader().use {
				geomap_loader = it.readText()
			}
		}
		return geomap_loader!!
	}

	fun ckeditorJS(context: Context): String {
		if (ckeditorJS != null) {
			return ckeditorJS!!
		}
		context.resources.assets.open("ckeditor.js").use { fileInput ->
			fileInput.bufferedReader().use {
				ckeditorJS = it.readText()
			}
		}
		return ckeditorJS!!
	}

	fun noteChildrenTemplateHTML(context: Context): String {
		if (noteChildren_TPL != null) {
			return noteChildren_TPL!!
		}
		context.resources.assets.open("noteChildren_TPL.html").use { fileInput ->
			fileInput.bufferedReader().use {
				noteChildren_TPL = it.readText()
			}
		}
		return noteChildren_TPL!!
	}

	fun noteEditableTemplateHTML(context: Context): String {
		if (noteEditable_TPL != null) {
			return noteEditable_TPL!!
		}
		context.resources.assets.open("noteEditable_TPL.html").use { fileInput ->
			fileInput.bufferedReader().use {
				noteEditable_TPL = it.readText()
			}
		}
		return noteEditable_TPL!!
	}

	fun noteEditableJS(context: Context): String {
		if (noteEditable_JS != null) {
			return noteEditable_JS!!
		}
		context.resources.assets.open("noteEditable.js").use { fileInput ->
			fileInput.bufferedReader().use {
				noteEditable_JS = it.readText()
			}
		}
		return noteEditable_JS!!
	}

	fun webAsset(context: Context, url: String): InputStream? = synchronized(this) {
		webAssetInternal(context, url)
	}

	fun docAsset(context: Context, docName: String): InputStream? = synchronized(this) {
		docAssetInternal(context, docName)
	}

	fun stylesheet(context: Context, filename: String): InputStream? = synchronized(this) {
		stylesheetInternal(context, filename)
	}

	private fun webAssetInternal(context: Context, url: String): InputStream? {
		val webZipCached = context.cacheDir.toPath().resolve("web.zip")
		if (webZipCached.notExists() || Preferences.webAssetsVersion() < LATEST_WEB_ASSETS) {
			Log.d(TAG, "updating cached web.zip")
			webZipCached.deleteIfExists()
			context.resources.assets.open("web.zip").use { inputStream ->
				inputStream.buffered().use { inputBuffered ->
					webZipCached.outputStream().use { outputStream ->
						outputStream.buffered().use { outputBuffered ->
							inputBuffered.copyTo(outputBuffered)
						}
					}
				}
			}
			Preferences.setWebAssetsVersion(LATEST_WEB_ASSETS)
		}
		val zip = ZipFile(webZipCached.toFile())
		val zipEntry = zip.getEntry(url.replace('/', '_')) ?: return null
		return zip.getInputStream(zipEntry)
	}

	private fun docAssetInternal(context: Context, docName: String): InputStream? {
		val docZipCached = context.cacheDir.toPath().resolve("doc_notes.zip")
		if (docZipCached.notExists() || Preferences.docAssetsVersion() < LATEST_DOC_ASSETS) {
			Log.d(TAG, "updating cached doc_notes.zip")
			docZipCached.deleteIfExists()
			context.resources.assets.open("doc_notes.zip").use { inputStream ->
				inputStream.buffered().use { inputBuffered ->
					docZipCached.outputStream().use { outputStream ->
						outputStream.buffered().use { outputBuffered ->
							inputBuffered.copyTo(outputBuffered)
						}
					}
				}
			}
			Preferences.setDocAssetsVersion(LATEST_DOC_ASSETS)
		}
		val zip = ZipFile(docZipCached.toFile())
		val zipEntry = zip.getEntry("en/${docName}.html") ?: return null
		return zip.getInputStream(zipEntry)
	}

	private fun stylesheetInternal(context: Context, filename: String): InputStream? {
		val cssZipCached = context.cacheDir.toPath().resolve("stylesheets.zip")
		if (cssZipCached.notExists() || Preferences.cssAssetsVersion() < LATEST_CSS_ASSETS) {
			Log.d(TAG, "updating cached stylesheets.zip")
			cssZipCached.deleteIfExists()
			context.resources.assets.open("stylesheets.zip").use { inputStream ->
				inputStream.buffered().use { inputBuffered ->
					cssZipCached.outputStream().use { outputStream ->
						outputStream.buffered().use { outputBuffered ->
							inputBuffered.copyTo(outputBuffered)
						}
					}
				}
			}
			Preferences.setCssAssetsVersion(LATEST_CSS_ASSETS)
		}
		val zip = ZipFile(cssZipCached.toFile())
		val zipEntry = zip.getEntry(filename) ?: return null
		return zip.getInputStream(zipEntry)
	}

	fun trimMemory() {
		ckeditorJS = null
		excalidraw_TPL = null
		geomap_TPL = null
		excalidraw_loader = null
		geomap_loader = null
		noteChildren_TPL = null
		noteEditable_TPL = null
		noteEditable_JS = null
	}
}
