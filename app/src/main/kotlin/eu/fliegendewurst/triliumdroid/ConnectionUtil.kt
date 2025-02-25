package eu.fliegendewurst.triliumdroid

import android.content.Context
import android.security.KeyChain
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import eu.fliegendewurst.triliumdroid.service.Util
import eu.fliegendewurst.triliumdroid.util.CookieJar
import eu.fliegendewurst.triliumdroid.util.GetSSID
import eu.fliegendewurst.triliumdroid.util.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


@OptIn(ExperimentalEncodingApi::class)
object ConnectionUtil {
	private const val TAG: String = "ConnectionUtil"
	private val JSON: MediaType = "application/json; charset=utf-8".toMediaTypeOrNull()!!
	private var client: OkHttpClient? = null
	private var server: String = "http://0.0.0.0"
	private var password: String = "aaaaaa" // easy to catch in logs
	var instanceId: String? = null
	private var syncVersion: Int = Cache.Versions.SYNC_VERSION
	private var loginFails = 0

	private var dbMismatch: Boolean = false
	private var loginSuccess: Boolean = false
	private var connectSuccess: Boolean = false

	suspend fun setup(
		activity: AppCompatActivity,
		callback: () -> Unit,
		callbackError: (Exception) -> Unit
	) {
		if (client == null) {
			resetClient(activity) {
				activity.lifecycleScope.launch {
					setupInternal(callback, callbackError)
				}
			}
		} else {
			setupInternal(callback, callbackError)
		}
	}

	private suspend fun setupInternal(
		callback: () -> Unit,
		callbackError: (Exception) -> Unit
	) {
		server = Preferences.hostname()!!
		password = Preferences.password()!!
		instanceId = Preferences.instanceId()
		if (instanceId == null) {
			instanceId = "mobile" + Util.randomString(6)
			Preferences.setInstanceId(instanceId!!)
		}
		Log.d(TAG, "setup with server = $server, instance ID = $instanceId")
		val documentSecret = Preferences.documentSecret()
		if (documentSecret == null) {
			loginSuccess = false
			fetch("/api/setup/sync-seed", null, true, {
				if (!Cache.Versions.SUPPORTED_SYNC_VERSIONS.contains(it.getInt("syncVersion"))) {
					callbackError(IllegalStateException("unsupported sync version"))
					return@fetch
				}
				syncVersion = it.getInt("syncVersion")
				val opts = it.getJSONArray("options")
				for (i in 0 until opts.length()) {
					val opt = opts.getJSONObject(i)
					val name = opt.getString("name")
					val value = opt.getString("value")
					if (name == "documentSecret") {
						Preferences.setDocumentSecret(value)
						Preferences.setSyncVersion(syncVersion)
						loginSuccess = true
						runBlocking {
							connect(server, callback, callbackError)
						}
						return@fetch
					}
				}
				callbackError(IllegalStateException("failed to get documentSecret"))
			}, {
				Log.e(TAG, "failed to fetch sync seed ", it)
				callbackError(it)
			})
		} else {
			loginSuccess = true
			connect(server, callback, callbackError)
		}
	}

	/**
	 * May **block** the I/O thread if [KeyChain.getPrivateKey] does not return quickly.
	 */
	suspend fun resetClient(activity: AppCompatActivity, callback: () -> Unit) {
		client = null

		val limitToSSID = Preferences.syncSSID()
		if (limitToSSID != null) {
			GetSSID(activity) {
				if (limitToSSID == it) {
					activity.lifecycleScope.launch {
						reset(activity.applicationContext, callback)
					}
				}
			}.getSSID()
			return
		}
		reset(activity.applicationContext, callback)
	}

	private suspend fun reset(appContext: Context, done: () -> Unit) = withContext(Dispatchers.IO) {
		var clientBuilder = OkHttpClient.Builder()
			.cookieJar(CookieJar())

		var pk: PrivateKey? = null
		var chain: Array<X509Certificate>? = null

		val mTLS = Preferences.mTLS()
		if (mTLS != null) {
			try {
				pk = KeyChain.getPrivateKey(appContext, mTLS)
				chain = KeyChain.getCertificateChain(appContext, mTLS)
			} catch (t: Throwable) {
				Log.e(TAG, "failed to read mTLS key ", t)
			}
			if (pk == null || chain == null) {
				Log.e(TAG, "got null response from KeyChain getPrivateKey or getCertificateChain")
			}
		}

		val trustManagerFactory =
			TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
		val ks: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
			load(null)
		}
		val serverCert = ks.getCertificate("syncServer")
		trustManagerFactory.init(ks)
		val trustManagers = trustManagerFactory.trustManagers


		val keyManagerFactory =
			KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
		keyManagerFactory.init(null, null)

		val km = object : X509KeyManager {
			override fun getClientAliases(
				keyType: String?,
				issuers: Array<Principal>
			): Array<String> {
				return if (mTLS != null) {
					arrayOf(mTLS)
				} else {
					arrayOf()
				}
			}

			override fun chooseClientAlias(
				keyType: Array<out String>?,
				issuers: Array<out Principal>?,
				socket: Socket?
			): String {
				return mTLS ?: ""
			}

			override fun getServerAliases(
				keyType: String?,
				issuers: Array<Principal>
			): Array<String> {
				return arrayOf()
			}

			override fun chooseServerAlias(
				keyType: String?,
				issuers: Array<Principal>,
				socket: Socket
			): String {
				return ""
			}

			override fun getCertificateChain(alias: String?): Array<X509Certificate> {
				return chain ?: emptyArray()
			}

			override fun getPrivateKey(alias: String?): PrivateKey {
				return pk!!
			}
		}

		val sslContext = SSLContext.getInstance("TLS")
		sslContext.init(arrayOf(km), trustManagers, null)

		clientBuilder = clientBuilder
			.sslSocketFactory(sslContext.socketFactory, trustManagers[0] as X509TrustManager)

		if (serverCert != null) {
			clientBuilder =
				clientBuilder.hostnameVerifier { _, session ->
					session.peerCertificates.contains(
						serverCert
					)
				}
		}

		client = clientBuilder
			.build()
		done.invoke()
	}

	fun fetch(
		path: String,
		formBody: FormBody?,
		userPasswordAuth: Boolean,
		callbackOk: (JSONObject) -> Unit,
		callbackError: (Exception) -> Unit
	) {
		try {
			Request.Builder().url("$server$path")
		} catch (e: Exception) {
			// bad URL
			callbackError.invoke(IllegalArgumentException("bad fetch URL $server$path"))
			return
		}
		var reqBuilder = Request.Builder()
			.url("$server$path")
		reqBuilder = if (formBody != null) {
			reqBuilder.post(formBody)
		} else {
			reqBuilder.get()
		}
		if (userPasswordAuth) {
			reqBuilder.addHeader(
				"trilium-cred",
				Base64.encode("user:$password".encodeToByteArray())
			)
		}
		val req = reqBuilder.build()
		Log.i(TAG, req.url.encodedPath)
		try {
			client!!.newCall(req).enqueue(object : Callback {
				override fun onResponse(call: Call, response: Response) {
					response.use {
						val resp = response.body!!.string()
						try {
							if (response.code == 401 && resp == "Incorrect password") {
								callbackError(IncorrectPasswordException("$path returned 401"))
								return
							}
							if (response.code != 200) {
								callbackError(IllegalStateException("bad sync-seed response code ${response.code}, $resp"))
								return
							}
							val obj = JSONObject(resp)
							callbackOk(obj)
						} catch (e: Exception) {
							callbackError(e)
						}
					}
				}

				override fun onFailure(call: Call, e: IOException) {
					Log.e(TAG, "login failed", e)

					loginFails++
					callbackError(e)
				}
			})
		} catch (e: Exception) {
			Log.e(TAG, "error ", e)
		}
	}

	private fun calculateHMAC(documentSecret: String, timestamp: String): String {
		val hMacSHA256 = Mac.getInstance("HmacSHA256")
		val secretKey = SecretKeySpec(documentSecret.encodeToByteArray(), "HmacSHA256")
		hMacSHA256.init(secretKey)
		val data = hMacSHA256.doFinal(timestamp.toByteArray())
		return Base64.encode(data)
	}

	private suspend fun connect(
		server: String,
		callback: () -> Unit,
		callbackError: (Exception) -> Unit
	): Unit = withContext(Dispatchers.IO) {
		if (client == null) {
			Log.w(TAG, "client not active")
			callbackError(IllegalStateException("client not active"))
			return@withContext
		}
		if (server.isEmpty()) {
			Log.e(TAG, "empty sync URL")
			callbackError(IllegalStateException("empty sync URL"))
			return@withContext
		}
		try {
			Request.Builder().url(server)
		} catch (e: Exception) {
			// bad URL
			callbackError.invoke(IllegalArgumentException("bad sync URL"))
			return@withContext
		}

		val utc = Cache.utcDateModified()
		val hash = calculateHMAC(Preferences.documentSecret()!!, utc)
		val jsonObject = JSONObject()
		jsonObject.put("timestamp", utc)
		jsonObject.put("hash", hash)
		syncVersion = Preferences.syncVersion() ?: syncVersion
		jsonObject.put("syncVersion", syncVersion)
		val req = Request.Builder()
			.url("$server/api/login/sync")
			.post(jsonObject.toString().toRequestBody(JSON))
			.build()
		Log.i(TAG, req.url.encodedPath)
		dbMismatch = false
		connectSuccess = false
		client!!.newCall(req).enqueue(object : Callback {
			override fun onResponse(call: Call, response: Response) {
				val resp = response.body?.string() ?: "(no body)"
				if (response.code != 200) {
					Log.e(TAG, "login received response $resp")
					try {
						val json = JSONObject(resp)
						val msg = json.getString("message")
						if (msg.startsWith("Sync login credentials are incorrect. It looks like you're trying to sync two different initialized documents which is not possible.")) {
							dbMismatch = true
							callbackError(MismatchedDatabaseException)
							return
						}
						for (version in Cache.Versions.SUPPORTED_SYNC_VERSIONS) {
							if (msg.startsWith("Non-matching sync versions, local is version $version, remote is")) {
								Preferences.setSyncVersion(version)
								runBlocking {
									connect(server, callback, callbackError)
								}
								return
							}
						}
						callbackError(IllegalStateException(msg))
						return
					} catch (_: Exception) {
					}
					Log.e(TAG, "login received response $resp")
					callbackError(IllegalStateException("bad response code for login"))
					return
				}
				Log.d(TAG, "login received response $resp")
				response.use {
					loginFails = 0
					connectSuccess = true
					callback()
				}
			}

			override fun onFailure(call: Call, e: IOException) {
				Log.e(TAG, "login failed", e)

				loginFails++
				callbackError(e)
			}
		})
	}

	suspend fun doSyncRequest(
		uri: String,
		callback: (JSONObject) -> Unit,
		callbackError: (Exception) -> Unit
	) = withContext(Dispatchers.IO) {
		val req = Request.Builder()
			.get()
			.url("$server$uri")
			.build()
		Log.i(TAG, uri)
		client!!.newCall(req).enqueue(object : Callback {
			override fun onResponse(call: Call, response: Response) {
				response.use {
					val body = response.body!!
					if (body.contentLength() >= 30000000) {
						Log.e(TAG, "sync data too big: ${body.contentLength()} bytes > 30 MB")
						callbackError(SyncResponseTooBigException)
						return
					}
					callback(JSONObject(body.string()))
				}
			}

			override fun onFailure(call: Call, e: IOException) {
				Log.e(TAG, "failed to fetch sync data", e)
				callbackError(e)
			}
		})
	}

	suspend fun doSyncPushRequest(uri: String, data: JSONObject) = withContext(Dispatchers.IO) {
		val dataBody = data.toString(0)
		Log.i(TAG, "syncing ${data.length()} bytes of data")
		val req = Request.Builder()
			.header("pageCount", "1")
			.header("pageIndex", "0")
			.header("requestId", "n/a")
			.put(dataBody.toRequestBody("application/json".toMediaType()))
			.url("$server$uri")
			.build()
		Log.i(TAG, uri)
		client!!.newCall(req).enqueue(object : Callback {
			override fun onResponse(call: Call, response: Response) {
				response.use {}
			}

			override fun onFailure(call: Call, e: IOException) {
				Log.e(TAG, "failed to push sync data", e)
			}
		})
	}

	/**
	 * Get the "app info" of the connected sync server.
	 * See <a href="https://github.com/zadam/trilium/blob/master/src/services/app_info.js">Trilium app_info service</a>.
	 */
	suspend fun getAppInfo(callback: (AppInfo?) -> Unit) = withContext(Dispatchers.IO) {
		val req = Request.Builder()
			.url("$server/api/app-info")
			.build()
		Log.i(TAG, req.url.encodedPath)
		client!!.newCall(req).enqueue(object : Callback {
			override fun onResponse(call: Call, response: Response) {
				val resp = response.body!!.string()
				Log.d(TAG, "app-info $resp")
				try {
					val json = JSONObject(resp)
					callback(
						AppInfo(
							json.getString("appVersion"),
							json.getInt("dbVersion"),
							json.getInt("syncVersion"),
							json.getString("buildDate"),
							json.getString("buildRevision"),
							json.getString("dataDirectory"),
							json.getString("clipperProtocolVersion"),
							json.getString("utcDateTime")
						)
					)
				} catch (e: JSONException) {
					callback(null)
				}
			}

			override fun onFailure(call: Call, e: IOException) {
				callback(null)
			}
		})
	}

	fun status(): SyncStatus {
		return SyncStatus(dbMismatch, loginSuccess, connectSuccess, Cache.lastSync)
	}

	data class AppInfo(
		val appVersion: String, // packageJson.version,
		val dbVersion: Int, // APP_DB_VERSION,
		val syncVersion: Int, // SYNC_VERSION,
		val buildDate: String, // build.buildDate,
		val buildRevision: String, // build.buildRevision,
		val dataDirectory: String, // TRILIUM_DATA_DIR,
		val clipperProtocolVersion: String, // CLIPPER_PROTOCOL_VERSION,
		val utcDateTime: String, // new Date().toISOString() // for timezone inference
	)

	data class SyncStatus(
		val dbMismatch: Boolean,
		val loginSuccess: Boolean,
		val connectSuccess: Boolean,
		val lastSync: Long?,
	)
}
