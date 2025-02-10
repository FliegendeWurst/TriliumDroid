package eu.fliegendewurst.triliumdroid.util

import android.content.Context
import eu.fliegendewurst.triliumdroid.Cache
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object CrashReport {
	fun saveReport(context: Context, thread: Thread, throwable: Throwable) {
		val crashreportDir = File(context.filesDir.parent, "crash-reports/")
		if (!crashreportDir.exists()) {
			crashreportDir.mkdir()
		}
		val file = File(crashreportDir, Cache.utcDateModified())
		val fos = FileOutputStream(file)
		try {
			val bytes =
				"\n\nThread: ${thread.name} (ID = ${thread.id})\nError: ${throwable}\nStacktrace:\n${throwable.stackTraceToString()}".encodeToByteArray()
			fos.write(bytes)
			fos.close()
		} catch (e: IOException) {
			// ignored
		}
	}

	fun pendingReports(context: Context): Array<out File> {
		val crashreportDir = File(context.filesDir.parent, "crash-reports/")
		if (!crashreportDir.exists()) {
			return emptyArray()
		}
		return crashreportDir.listFiles() ?: emptyArray()
	}
}