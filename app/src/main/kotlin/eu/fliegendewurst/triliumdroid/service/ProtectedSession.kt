package eu.fliegendewurst.triliumdroid.service

import android.util.Log
import eu.fliegendewurst.triliumdroid.util.Preferences
import org.bouncycastle.crypto.BufferedBlockCipher
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.BlockCipherPadding
import org.bouncycastle.crypto.paddings.PKCS7Padding
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.jcajce.provider.digest.SHA1
import java.lang.ref.WeakReference
import java.security.SecureRandom


object ProtectedSession {
	private const val TAG = "ProtectedSession"
	private var rng: SecureRandom = SecureRandom()
	private var key: ByteArray? = null
	private var toNotify: MutableList<WeakReference<NotifyProtectedSessionEnd>> = mutableListOf()

	// code based on
	// https://github.com/TriliumNext/Notes/blob/develop/src/services/encryption/my_scrypt.ts
	// https://github.com/TriliumNext/Notes/blob/develop/src/services/encryption/password_encryption.ts
	// https://github.com/TriliumNext/Notes/blob/develop/src/services/encryption/data_encryption.ts

	fun isActive() = key != null

	fun enter(): String? {
		val password = Preferences.password()?.encodeToByteArray() ?: return "no password"
		val salt = Option.passwordDerivedKeySalt()?.encodeToByteArray() ?: return "no salt"
		val encryptedDataKey = Option.encryptedDataKey() ?: return "no encrypted data key"

		val passwordDerivedKey = SCrypt.generate(password, salt, 16384, 8, 1, 32)

		key = decryptInternal(passwordDerivedKey, encryptedDataKey)

		return null
	}

	fun decrypt(cipherTextWithIV: ByteArray): ByteArray? {
		return decryptInternal(key ?: return null, cipherTextWithIV)
	}

	private fun decryptInternal(key: ByteArray, cipherTextWithIV: ByteArray): ByteArray {
		val ivLength = if (cipherTextWithIV.size % 16 == 0) {
			16
		} else {
			13
		}
		val iv = cipherTextWithIV.sliceArray(0 until ivLength)

		val cipherText = cipherTextWithIV.sliceArray(ivLength until cipherTextWithIV.size)

		val padding: BlockCipherPadding = PKCS7Padding()
		val cipher: BufferedBlockCipher =
			PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(AESEngine()), padding)
		cipher.reset()
		cipher.init(false, ParametersWithIV(KeyParameter(pad(key)), pad(iv)))

		val outputSize = cipher.getOutputSize(cipherText.size)
		val workingBuffer = ByteArray(outputSize)
		var len = cipher.processBytes(cipherText, 0, cipherText.size, workingBuffer, 0)
		len += cipher.doFinal(workingBuffer, len)
		val data = workingBuffer.sliceArray(4 until len)
		// verify checksum
		val sha1 = SHA1.Digest()
		sha1.update(data)
		val checksum = sha1.digest().sliceArray(0 until 4)
		if (!checksum.contentEquals(workingBuffer.sliceArray(0 until 4))) {
			Log.e(
				TAG,
				"Checksum mismatch after decryption! Ciphertext length ${cipherTextWithIV.size}"
			)
		}
		return data
	}

	fun encrypt(plainText: ByteArray): ByteArray? {
		return encryptInternal(key ?: return null, plainText)
	}

	private fun encryptInternal(key: ByteArray, data: ByteArray): ByteArray {
		val sha1 = SHA1.Digest()
		sha1.update(data)
		val checksum = sha1.digest().sliceArray(0 until 4)
		val plainText = checksum + data

		val iv = ByteArray(16)
		rng.nextBytes(iv)

		val padding: BlockCipherPadding = PKCS7Padding()
		val cipher: BufferedBlockCipher =
			PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(AESEngine()), padding)
		cipher.reset()
		cipher.init(true, ParametersWithIV(KeyParameter(pad(key)), iv))

		val outputSize = cipher.getOutputSize(plainText.size)
		val workingBuffer = ByteArray(outputSize)
		var len = cipher.processBytes(plainText, 0, plainText.size, workingBuffer, 0)
		len += cipher.doFinal(workingBuffer, len)
		return iv + workingBuffer.sliceArray(0 until len)
	}

	private fun pad(data: ByteArray): ByteArray {
		if (data.size > 16) {
			return data.sliceArray(0 until 16)
		} else if (data.size < 16) {
			val zeros = ByteArray(16 - data.size)

			return zeros + data
		} else {
			return data
		}
	}

	fun addListener(o: NotifyProtectedSessionEnd) {
		toNotify.add(WeakReference(o))
	}

	fun leave() {
		toNotify.mapNotNull { it.get() }
			.forEach(NotifyProtectedSessionEnd::sessionExpired)
		toNotify.clear()
		key = null
	}

	interface NotifyProtectedSessionEnd {
		fun sessionExpired()
	}
}
