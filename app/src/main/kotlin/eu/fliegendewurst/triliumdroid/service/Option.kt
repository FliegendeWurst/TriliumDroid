package eu.fliegendewurst.triliumdroid.service

import eu.fliegendewurst.triliumdroid.Cache
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object Option {
	fun passwordDerivedKeySalt() = getString("passwordDerivedKeySalt")
	fun encryptedDataKey() = getString("encryptedDataKey").let {
		if (it != null) {
			Base64.decode(it.encodeToByteArray())
		} else {
			null
		}
	}

	private fun getString(name: String): String? {
		Cache.db!!.rawQuery("SELECT value FROM options WHERE name = ?", arrayOf(name))
			.use {
				if (it.moveToFirst()) {
					return it.getString(0)
				}
			}
		return null
	}
}
