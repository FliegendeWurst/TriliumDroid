package eu.fliegendewurst.triliumdroid.sync

import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class SyncTrustManagerTest {
	private val ca = HeldCertificate.Builder().certificateAuthority(0).commonName("CA").build()
	private val caSigned = HeldCertificate.Builder().commonName("server").signedBy(ca).build()
	private val selfSigned = HeldCertificate.Builder().commonName("self-signed").build()

	/**
	 * Stand-in for the system trust store: trusts chains issued by [ca] only.
	 */
	private class FakeSystemTrustManager(private val ca: X509Certificate) : X509TrustManager {
		var serverChecks = 0

		override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
			throw CertificateException("unexpected client check")
		}

		override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
			serverChecks++
			if (chain.isNullOrEmpty() || chain[0].issuerX500Principal != ca.subjectX500Principal) {
				throw CertificateException("Trust anchor for certification path not found.")
			}
		}

		override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf(ca)
	}

	@Test
	fun systemTrustedChainAcceptedWithoutUserCertificate() {
		val system = FakeSystemTrustManager(ca.certificate)
		SyncTrustManager(system, null)
			.checkServerTrusted(arrayOf(caSigned.certificate, ca.certificate), "RSA")
		assertEquals(1, system.serverChecks)
	}

	@Test
	fun systemTrustedChainAcceptedWithOtherUserCertificate() {
		// e.g. the server's certificate was renewed after the user accepted the previous one
		val system = FakeSystemTrustManager(ca.certificate)
		SyncTrustManager(system, selfSigned.certificate)
			.checkServerTrusted(arrayOf(caSigned.certificate, ca.certificate), "RSA")
		assertEquals(1, system.serverChecks)
	}

	@Test
	fun userCertificateAcceptedWithoutSystemCheck() {
		val system = FakeSystemTrustManager(ca.certificate)
		SyncTrustManager(system, selfSigned.certificate)
			.checkServerTrusted(arrayOf(selfSigned.certificate), "RSA")
		assertEquals(0, system.serverChecks)
	}

	@Test
	fun untrustedCertificateRejected() {
		val system = FakeSystemTrustManager(ca.certificate)
		assertThrows(CertificateException::class.java) {
			SyncTrustManager(system, null)
				.checkServerTrusted(arrayOf(selfSigned.certificate), "RSA")
		}
		val other = HeldCertificate.Builder().commonName("other").build()
		assertThrows(CertificateException::class.java) {
			SyncTrustManager(system, other.certificate)
				.checkServerTrusted(arrayOf(selfSigned.certificate), "RSA")
		}
	}

	@Test
	fun acceptedIssuersAreTheSystemOnes() {
		val system = FakeSystemTrustManager(ca.certificate)
		assertEquals(
			listOf(ca.certificate),
			SyncTrustManager(system, selfSigned.certificate).acceptedIssuers.toList()
		)
	}
}
