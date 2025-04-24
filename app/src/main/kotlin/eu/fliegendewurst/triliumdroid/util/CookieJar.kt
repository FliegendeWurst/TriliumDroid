package eu.fliegendewurst.triliumdroid.util

import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Simple cookie jar to save/load cookies per host.
 */
class CookieJar : CookieJar {
	companion object {
		const val TAG: String = "CookieJar"
	}

	private var cookieStorage: MutableMap<String, MutableList<Cookie>> = mutableMapOf()

	override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
		val host = url.host
		val cookieStore =
			cookieStorage.computeIfAbsent(host) { return@computeIfAbsent mutableListOf() }
		for (cookie in cookies) {
			Log.i(
				TAG,
				"store: path=${cookie.path} name=${cookie.name} value.len()=${cookie.value.length}"
			)
			var added = false
			for (i in cookieStore.indices) {
				if (cookieStore[i].name == cookie.name) {
					cookieStore[i] = cookie
					added = true
					break
				}
			}
			if (!added) {
				cookieStore.add(cookie)
			}
		}
	}

	override fun loadForRequest(url: HttpUrl): List<Cookie> {
		val host = url.host
		return cookieStorage[host] ?: emptyList()
	}

}
