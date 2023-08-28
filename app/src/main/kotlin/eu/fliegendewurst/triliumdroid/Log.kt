package eu.fliegendewurst.triliumdroid


object Log {
	fun d(tag: String, msg: String) {
		android.util.Log.d(tag, msg)
	}

	fun i(tag: String, msg: String) {
		android.util.Log.i(tag, msg)
	}

	fun e(tag: String, msg: String) {
		android.util.Log.e(tag, msg)
	}

	fun e(tag: String, msg: String, t: Throwable) {
		android.util.Log.e(tag, msg, t)
	}
}