package eu.fliegendewurst.triliumdroid

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


@OptIn(ExperimentalEncodingApi::class)
object ConnectionUtil {
	private const val TAG: String = "ConnectionUtil"
	private val JSON: MediaType = "application/json; charset=utf-8".toMediaTypeOrNull()!!
	private var client: OkHttpClient? = null
	private var server: String = "http://0.0.0.0"
	private var password: String = "aaaaaa" // easy to catch in logs
	private var prefs: SharedPreferences? = null
	private var syncVersion: Int = Cache.CacheDbHelper.SYNC_VERSION
	private var loginFails = 0

	fun setup(prefs: SharedPreferences, callback: () -> Unit, callbackError: (Exception) -> Unit) {
		if (client == null) {
			client = OkHttpClient.Builder()
				.cookieJar(object : CookieJar {
					// TODO: this is a terrible cookie jar
					private var cookieStore: MutableList<Cookie> = ArrayList()
					override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
						for (cookie in cookies) {
							Log.i(
								TAG,
								"cookie ${cookie.path} ${cookie.name} = ${cookie.value.length} characters"
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
						return cookieStore
					}
				})
				.build()
		}

		ConnectionUtil.prefs = prefs
		server = prefs.getString("hostname", null)!!
		password = prefs.getString("password", null)!!
		val documentSecret = prefs.getString("documentSecret", null)
		if (documentSecret == null) {
			fetch("/api/setup/sync-seed", null, {
				if (it.getInt("syncVersion") != Cache.CacheDbHelper.SYNC_VERSION && it.getInt("syncVersion") != Cache.CacheDbHelper.SYNC_VERSION_0_63_3) {
					callbackError(IllegalStateException("wrong sync version"))
					return@fetch
				}
				syncVersion = it.getInt("syncVersion")
				val opts = it.getJSONArray("options")
				for (i in 0 until opts.length()) {
					val opt = opts.getJSONObject(i)
					val name = opt.getString("name")
					val value = opt.getString("value")
					if (name == "documentSecret") {
						prefs.edit().putString("documentSecret", value)
							.putInt("syncVersion", syncVersion).apply()
						connect(server, callback, callbackError)
						return@fetch
					}
				}
				callbackError(IllegalStateException("failed to get documentSecret"))
			}, {
				Log.e(TAG, "failed to fetch sync seed ", it)
				callbackError(it)
			})
		} else {
			connect(server, callback, callbackError)
		}
	}

	private fun fetch(
		path: String,
		formBody: FormBody?,
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
		reqBuilder.addHeader("trilium-cred", Base64.encode("user:$password".encodeToByteArray()))
		val req = reqBuilder.build()
		Log.i(TAG, req.url.encodedPath)
		try {
			client!!.newCall(req).enqueue(object : Callback {
				override fun onResponse(call: Call, response: Response) {
					response.use {
						val resp = response.body!!.string()
						val obj = JSONObject(resp)
						if (response.code != 200) {
							callbackError(IllegalStateException("bad response code"))
							return
						}
						callbackOk(obj)
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

	private fun hmac(documentSecret: String, timestamp: String): String {
		val hMacSHA256 = Mac.getInstance("HmacSHA256")
		val secretKey = SecretKeySpec(documentSecret.encodeToByteArray(), "HmacSHA256")
		hMacSHA256.init(secretKey)
		val data = hMacSHA256.doFinal(timestamp.toByteArray())
		return Base64.encode(data)
	}

	private fun connect(
		server: String,
		callback: () -> Unit,
		callbackError: (Exception) -> Unit
	) {
		if (server.isEmpty()) {
			Log.e(TAG, "empty sync URL")
			return
		}
		try {
			Request.Builder().url(server)
		} catch (e: Exception) {
			// bad URL
			callbackError.invoke(IllegalArgumentException("bad sync URL"))
			return
		}

		val utc = Cache.utcDateModified()
		val hash = hmac(prefs!!.getString("documentSecret", null)!!, utc)
		val jsonObject = JSONObject()
		jsonObject.put("timestamp", utc)
		jsonObject.put("hash", hash)
		syncVersion = prefs?.getInt("syncVersion", syncVersion) ?: syncVersion
		jsonObject.put("syncVersion", syncVersion)
		val req = Request.Builder()
			.url("$server/api/login/sync")
			.post(jsonObject.toString().toRequestBody(JSON))
			.build()
		Log.i(TAG, req.url.encodedPath)
		client!!.newCall(req).enqueue(object : Callback {
			override fun onResponse(call: Call, response: Response) {
				val resp = response.body!!.string()
				if (response.code != 200) {
					Log.e(TAG, "login received response $resp")
					try {
						val json = JSONObject(resp)
						val msg = json.getString("message")
						if (msg.startsWith("Non-matching sync versions, local is version 32, remote is 33.")) {
							prefs?.edit()?.putInt("syncVersion", Cache.CacheDbHelper.SYNC_VERSION_0_63_3)?.apply()
							connect(server, callback, callbackError)
							return
						}
						callbackError(IllegalStateException())
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

	fun doSyncRequest(uri: String, callback: (JSONObject) -> Unit) {
		val req = Request.Builder()
			.get()
			.url("$server$uri")
			.build()
		Log.i(TAG, uri)
		client!!.newCall(req).enqueue(object : Callback {
			override fun onResponse(call: Call, response: Response) {
				response.use {
					callback(JSONObject(response.body!!.string()))
				}
			}

			override fun onFailure(call: Call, e: IOException) {
				Log.e(TAG, "failed to fetch sync data", e)
			}
		})
	}

	fun doSyncPushRequest(uri: String, data: JSONObject) {
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
	fun getAppInfo(callback: (AppInfo?) -> Unit) {
		val req = Request.Builder()
			.url("$server/api/app-info")
			.build()
		Log.i(TAG, req.url.encodedPath)
		client!!.newCall(req).enqueue(object : Callback {
			override fun onResponse(call: Call, response: Response) {
				val resp = response.body!!.string()
				Log.d(TAG, "app-info $resp")
				var json: JSONObject? = null
				try {
					json = JSONObject(resp)
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
}