package eu.fliegendewurst.triliumdroid.util

import android.content.Context
import android.util.Log
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.outputStream
import kotlin.io.path.writer

object Assets {
	private const val TAG: String = "Assets"
	private const val LATEST_WEB_ASSETS = 2 // increment when updating web.zip

	private var excalidraw_TPL: String? = null
	private var excalidraw_loader: String? = null

	fun excalidrawTemplateHTML(context: Context): String {
		if (excalidraw_TPL != null) {
			return excalidraw_TPL!!
		}
		context.resources.assets.open("excalidraw_TPL.html").use { fileInput ->
			fileInput.bufferedReader().use {
				excalidraw_TPL = it.readText()
			}
		}
		Log.d(TAG, "loaded excalidraw template with ${excalidraw_TPL!!.length} characters")
		return excalidraw_TPL!!
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

	fun webAsset(context: Context, url: String): InputStream? = synchronized(this) {
		webAssetInternal(context, url)
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
}
