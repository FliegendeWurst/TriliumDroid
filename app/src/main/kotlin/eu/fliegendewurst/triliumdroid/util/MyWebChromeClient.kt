package eu.fliegendewurst.triliumdroid.util

import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity

/**
 * Logs calls to `console.log`, and forwards them to [MainActivity]
 */
class MyWebChromeClient(
	val activity: () -> MainActivity?,
	val callback: ((ConsoleMessage) -> Unit)?
) : WebChromeClient() {
	companion object {
		private const val TAG = "MyWebChromeClient"
	}

	override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
		if (consoleMessage == null) {
			return true /* handled */
		}
		Log.d(TAG, "console.log ${consoleMessage.message()}")
		val main = activity() ?: return true
		main.enableConsoleLogAction()
		callback?.invoke(consoleMessage)
		return true
	}
}
