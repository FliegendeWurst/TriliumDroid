package eu.fliegendewurst.triliumdroid

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import eu.fliegendewurst.triliumdroid.databinding.ActivitySetupBinding

class SetupActivity : AppCompatActivity() {
	companion object {
		private const val TAG: String = "SetupActivity"
	}
	private lateinit var binding: ActivitySetupBinding
	private lateinit var prefs: SharedPreferences

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivitySetupBinding.inflate(layoutInflater)
		prefs = PreferenceManager.getDefaultSharedPreferences(this)
		setContentView(binding.root)
		binding.server.setText(prefs.getString("hostname", "http://192.168.178.21:12783"))
		binding.password.setText(
			prefs.getString(
				"password",
				""
			)
		)
	}

	override fun onStop() {
		super.onStop()
		val server = binding.server.text.toString().trimEnd('/')
		val password = binding.password.text.toString()
		prefs.edit()
			.putString("hostname", server)
			.putString("password", password)
			.apply()
	}

	private fun setup() {
		val server = binding.server.text.toString().trimEnd('/')
		val password = binding.password.text.toString()
		if (server == "" || password == "") {
			return
		}
		val prefs = PreferenceManager.getDefaultSharedPreferences(this)
		prefs.edit()
			.putString("hostname", server)
			.putString("password", password)
			.apply()
		val looper = applicationContext.mainLooper
		val handler = Handler(looper)
		ConnectionUtil.connect(server, password, {
			handler.post {
				finish()
			}
		}, { error ->
			Log.e(TAG, "failed to connect to server", error)
			// TODO
		})
	}
}