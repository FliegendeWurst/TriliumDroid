package eu.fliegendewurst.triliumdroid.sync

import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Trusts the server certificate the user explicitly accepted (e.g. a self-signed one)
 * and any certificate chain trusted by the system (e.g. Let's Encrypt).
 * See https://github.com/FliegendeWurst/TriliumDroid/issues/123
 */
class SyncTrustManager(
	private val system: X509TrustManager,
	private val trustedServerCert: X509Certificate?
) : X509TrustManager {
	override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
		system.checkClientTrusted(chain, authType)
	}

	override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
		if (trustedServerCert != null && chain?.contains(trustedServerCert) == true) {
			return
		}
		system.checkServerTrusted(chain, authType)
	}

	override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers

	companion object {
		/**
		 * The trust manager of the platform's default trust store.
		 */
		fun systemDefault(): X509TrustManager {
			val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
			factory.init(null as KeyStore?)
			return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
		}
	}
}
