package eu.fliegendewurst.triliumdroid.service

import eu.fliegendewurst.triliumdroid.data.NoteId
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object Util {
	private val RANDOM_CHAR_POOL: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

	fun randomString(length: Int): String {
		return (1..length).map { RANDOM_CHAR_POOL.random() }.joinToString("")
	}

	fun newNoteId() = randomString(12)
	fun newDeleteId() = randomString(10)

	/**
	 * Checks if note ID is a regular user note, and not part of the help note tree.
	 */
	fun isRegularId(id: NoteId) = id.rawId().length == 12

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
