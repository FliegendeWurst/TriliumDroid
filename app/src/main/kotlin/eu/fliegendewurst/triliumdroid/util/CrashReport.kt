package eu.fliegendewurst.triliumdroid.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AlertDialog
import eu.fliegendewurst.triliumdroid.BuildConfig
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.database.Cache
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader


object CrashReport {
	private const val TAG = "CrashReport"

	fun saveReport(context: Context, thread: Thread, throwable: Throwable) {
		val crashreportDir = File(context.filesDir.parent, "crash-reports/")
		if (!crashreportDir.exists()) {
			crashreportDir.mkdir()
		}
		val file = File(crashreportDir, Cache.utcDateModified())
		val fos = FileOutputStream(file)

		try {
			var bytes = "\n\n"
			bytes += "Thread: ${thread.name} (ID = ${thread.id})\n"
			bytes += "Error: ${throwable}\n"
			bytes += "API Level: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})\n"
			bytes += "Stacktrace:\n"
			bytes += throwable.stackTraceToString()
			fos.write(bytes.encodeToByteArray())
			fos.flush()
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

	private fun pendingReports(context: Context): Array<out File> {
		val crashreportDir = File(context.filesDir.parent, "crash-reports/")
		if (!crashreportDir.exists()) {
			return emptyArray()
		}
		return crashreportDir.listFiles() ?: emptyArray()
	}

	fun showPendingReports(context: Context) {
		val lastReported = Preferences.lastReport() ?: "2020"
		val pendingReports = pendingReports(context).filter { x -> x.name > lastReported }
		val toReport = pendingReports.maxByOrNull { x -> x.name }
		if (toReport != null) {
			AlertDialog.Builder(context)
				.setTitle(context.getString(R.string.dialog_report_app_crash))
				.setMessage(
					context.getString(
						R.string.label_report_crash,
						toReport.name
					)
				)
				.setPositiveButton(
					android.R.string.ok
				) { _, _ ->
					Preferences.setLastReport(toReport.name)
					// read report and create email
					val intent = Intent(Intent.ACTION_SENDTO).apply {
						data = Uri.parse("mailto:")
						putExtra(
							Intent.EXTRA_EMAIL,
							arrayOf("arne.keller+triliumdroid-crash@posteo.de")
						)
						putExtra(
							Intent.EXTRA_SUBJECT,
							"TriliumDroid crash, version ${BuildConfig.VERSION_NAME}"
						)
						putExtra(Intent.EXTRA_TEXT, toReport.readText())
					}
					context.startActivity(Intent.createChooser(intent, "Choose email client:"))

					try {
						pendingReports.forEach { x -> x.deleteOnExit() }
					} catch (e: IOException) {
						Log.e(TAG, "failed to delete crash report ", e)
					}
				}
				.setNegativeButton(android.R.string.cancel, null)
				.setOnDismissListener {
					try {
						pendingReports.forEach { x -> x.delete() }
					} catch (e: IOException) {
						Log.e(TAG, "failed to delete crash report ", e)
					}
				}
				.setIconAttribute(android.R.attr.alertDialogIcon)
				.show()
		}
	}
}
