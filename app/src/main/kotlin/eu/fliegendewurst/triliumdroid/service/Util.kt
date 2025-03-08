package eu.fliegendewurst.triliumdroid.service

import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object Util {
	private val RANDOM_CHAR_POOL: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

	fun randomString(length: Int): String {
		return (1..length).map { RANDOM_CHAR_POOL.random() }.joinToString("")
	}

	fun newNoteId(): String {
		return randomString(12)
	}

	fun isRegularId(id: String) = id.length == 12

	@OptIn(ExperimentalEncodingApi::class)
	fun contentHash(data: ByteArray): String {
		val md = MessageDigest.getInstance("SHA-512")
		md.update(data)
		val digestBase64 = Base64.encode(md.digest())
			.replace('+', 'X')
			.replace('/', 'Y')
		return digestBase64.substring(0, 20)
	}
}
