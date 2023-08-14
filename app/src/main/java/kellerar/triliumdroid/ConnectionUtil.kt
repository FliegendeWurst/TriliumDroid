package kellerar.triliumdroid

import android.R.attr.data
import android.app.Activity
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.*


class ConnectionUtil {
	companion object {
		private const val TAG: String = "ConnectionUtil"
		private var client: OkHttpClient? = null
		var server: String = "http://0.0.0.0"
		private var prefs: SharedPreferences? = null
		private var csrf: String = ""
		private var loginFails = 0

		fun setup(activity: Activity, prefs: SharedPreferences, callback: () -> Unit) {
			this.prefs = prefs
			server = prefs.getString("hostname", null)!!
			val username = prefs.getString("username", null)!!
			val password = prefs.getString("password", null)!!
			connect(activity, server, username, password) {
				callback()
			}
		}

		fun connect(activity: Activity, server: String, username: String, password: String, callback: () -> Unit) {
			client = OkHttpClient.Builder()
					.cookieJar(object : CookieJar {
						// TODO: this is a terrible cookie jar
						private var cookieStore: MutableList<Cookie> = ArrayList()
						override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
							for (cookie in cookies) {
								Log.i(TAG, "cookie ${cookie.path} ${cookie.name} = ${cookie.value}")
								if (cookie.name == "_csrf") {
									csrf = cookie.value
								}
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

			this.server = server
			val obj = JSONObject()
			obj.put("password", password)
			Log.i(TAG, "$server/login")
			val form = FormBody.Builder()
					.add("password", password)
					.add("remember_me", "1")
					.build()
			val req = Request.Builder()
					.url("$server/login")
					.post(form)
					.build()
			Log.i(TAG, "/login")
			client!!.newCall(req).enqueue(object : Callback {
				override fun onResponse(call: Call, response: Response) {
					loginFails = 0
					callback()
				}

				override fun onFailure(call: Call, e: IOException) {
					Log.e(TAG, "fail", e)
					activity.runOnUiThread {
						Toast.makeText(
							activity, e.toString(),
							Toast.LENGTH_LONG
						).show()
					}

					loginFails++
					callback()
				}
			})
		}

		fun getNote(activity: Activity, id: String, callback: (Note) -> Unit) {
			if (csrf == "") {
				if (loginFails < 5) {
					setup(activity, prefs!!) {
						getNote(activity, id, callback)
					}
				}
				return
			}
			val req = Request.Builder()
					.get()
					.url("$server/api/notes/$id")
					.build()
			Log.i(TAG, "/api/notes/$id")
			client!!.newCall(req).enqueue(object : Callback {
				override fun onResponse(call: Call, response: Response) {
					val body = response.body
					if (body != null) {
						val resp = body.string()
						//Log.i(TAG, resp)
						if (resp != "Not authorized") {
							val json = JSONObject(resp)
							val note = Note(json)
							if (json.optBoolean("isContentAvailable") && json.opt("content") == null) {
								getRawNote(activity, id, callback)
							} else {
								callback(note)
							}
						}
					}
				}

				override fun onFailure(call: Call, e: IOException) {
					Log.e(TAG, "fail", e)
				}
			})
		}

		fun getRawNote(activity: Activity, id: String, callback: (Note) -> Unit) {
			if (csrf == "") {
				if (loginFails < 5) {
					setup(activity, prefs!!) {
						getRawNote(activity, id, callback)
					}
				}
			}
			val req = Request.Builder()
				.get()
				.url("$server/api/notes/$id/open")
				.build()
			Log.i(TAG, "/api/notes/$id/open")
			client!!.newCall(req).enqueue(object : Callback {
				override fun onResponse(call: Call, response: Response) {
					val note = Cache.notes[id] ?: return
					note.content = response.body!!.bytes()
					Cache.insertNote(note)
					//Log.i(TAG, "$id.size = ${note.content?.size}")
					callback(note)
					response.close()
				}

				override fun onFailure(call: Call, e: IOException) {
					Log.e(TAG, "failed to fetch raw note content", e)
				}
			})
		}

		fun doSyncRequest(activity: Activity, uri: String, callback: (JSONObject) -> Unit) {
			if (csrf == "") {
				Log.e(TAG, "tried to sync without token")
				return
			}
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
					Log.e(TAG, "failed to fetch raw note content", e)
				}
			})
		}

		fun getTreeData(activity: Activity, id: String, callback: () -> Unit) {
			if (csrf == "") {
				if (loginFails < 5) {
					setup(activity, prefs!!) {
						getTreeData(activity, id, callback)
					}
				}
				return
			}
			val salt = "asetnuh" // TODO: make random
			try {
				val hash = String(
						Base64.encode(
								MessageDigest.getInstance("SHA-1")
										.digest("$salt-$csrf".toByteArray()),
								Base64.DEFAULT
						)
				)
						.replace('+', '-')
						.replace('/', '_')
						.replace("=", "")
						.trim()
				val param = if (id != "root") { "?subTreeNoteId=$id" } else { "" }
				Log.i(TAG, "/api/tree$param")
				val req = Request.Builder()
						.url("$server/api/tree$param")
						.header("x-csrf-token", "$salt-$hash")
						.get()
						//.post(obj.toString().toRequestBody("application/json".toMediaType()))
						.build()
				client!!.newCall(req).enqueue(object : Callback {
					override fun onResponse(call: Call, response: Response) {
						try {
							//Log.i(TAG, response.code.toString())
							//Log.i(TAG, response.body.toString())
							val body = response.body!!
							val resp = body.string()
							//Log.i(TAG, resp)
							if (resp != """{message":"Invalid CSRF token"}""") {
								val obj = JSONObject(resp)
								val arr = obj.getJSONArray("branches")
								val branches = ArrayList<Branch>()
								Log.i(TAG, "caching ${arr.length()} branches")
								Cache.db!!.beginTransaction()
								for (i in 0 until arr.length()) {
									val branchObj = arr[i]
									if (branchObj is JSONObject) {
										val branch = Branch(
												branchObj.getString("branchId"),
												branchObj.getString("noteId"),
												branchObj.getString("parentNoteId"),
												branchObj.getInt("notePosition"),
												branchObj.getString("prefix"),
												branchObj.getBoolean("isExpanded"),
												TreeMap()
										)
										branches.add(branch)
										Cache.insertBranch(branch)
									}
								}
								for (branch in branches) {
									val parent = Cache.branches[branch.parentNote]
									//Log.i(TAG, "putting ${branch.id} into ${branch.parentNote}")
									parent?.children?.put(branch.position, branch)
								}
								val arrNotes = obj.getJSONArray("notes")
								for (i in 0 until arrNotes.length()) {
									val noteObj = arrNotes[i]
									if (noteObj is JSONObject) {
										//Log.i(TAG, "caching $noteObj")
										val note = Note(
												noteObj.getString("noteId"),
												noteObj.getString("mime"),
												noteObj.getString("title")
										)
										Cache.insertNote(note)
									}
								}
								Cache.db!!.endTransaction()
								callback()
							}} catch (t: Throwable) {
							Log.i(TAG, "digest error", t)
						}
						response.close()
					}

					override fun onFailure(call: Call, e: IOException) {
						Log.e(TAG, "fail: " + Log.getStackTraceString(e))
					}
				})
			} catch (t: Throwable) {
				Log.i(TAG, "digest error", t)
			}
		}
	}
}