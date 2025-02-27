package eu.fliegendewurst.triliumdroid.util

import android.content.Context
import eu.fliegendewurst.triliumdroid.database.Cache
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader


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
		} catch (e: IOException) {
			// ignored
		}
		try {
			val process = Runtime.getRuntime().exec("logcat -d")
			val bufferedReader = BufferedReader(
				InputStreamReader(process.inputStream)
			)
			val logcat = bufferedReader.readText()
			var logcatData = logcat.encodeToByteArray()
			if (logcatData.size > 110000) {
				logcatData = logcatData.sliceArray(logcatData.size - 100000 until logcatData.size)
			}
			fos.write("\nLogcat:\n".encodeToByteArray())
			fos.write(logcatData)
			fos.close()
			bufferedReader.close()
		} catch (e: IOException) {
			// ignored
		}
		try {
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
