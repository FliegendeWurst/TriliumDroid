package eu.fliegendewurst.triliumdroid

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import eu.fliegendewurst.triliumdroid.databinding.ActivitySetupBinding

class SetupActivity : AppCompatActivity() {
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
				"UNGHXdk0Ln22nDeIy98k4pDES9FBbrry8POEMMrkx4ovZLNyFGEDxCCE0yOHwP52riSTwCP5vhU7DbOkpdUOpelQJlJVwvA1u0k63pybca1yWBQFAFqEJ1NVT8iGDrIIdRmuVsod5v7gFQGRZCnxrtle3FHDUywufUNSROxrZfeT3gzqszsmn1yHFAJh6xqqSSKT6Ako9SqeDXmRIs8rfV8NmYom5Rur3TY4IlHxE2dKmldU9V7ii8vR9mNLHX4H"
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
		}, {
			// TODO
		})
	}
}