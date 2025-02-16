package eu.fliegendewurst.triliumdroid.util

import android.content.Context

object Resources {
	private var mindMap_TPL: String? = null

	fun mindMap_TPL(context: Context): String {
		if (mindMap_TPL != null) {
			return mindMap_TPL!!
		}
		mindMap_TPL = context.assets!!.open("mindMap_TPL.html").bufferedReader()
			.use { it.readText() }
		return mindMap_TPL!!
	}
}
