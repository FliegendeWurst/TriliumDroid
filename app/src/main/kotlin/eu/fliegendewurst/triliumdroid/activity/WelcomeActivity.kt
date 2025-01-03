package eu.fliegendewurst.triliumdroid.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import eu.fliegendewurst.triliumdroid.Cache
import eu.fliegendewurst.triliumdroid.SetupActivity
import eu.fliegendewurst.triliumdroid.databinding.ActivityWelcomeBinding

class WelcomeActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val binding = ActivityWelcomeBinding.inflate(layoutInflater)
		setContentView(binding.root)
		binding.buttonSetupSync.setOnClickListener {
			val intent = Intent(this, SetupActivity::class.java)
			startActivity(intent)
		}
	}

	override fun onStart() {
		super.onStart()
		val prefs = PreferenceManager.getDefaultSharedPreferences(this)
		if (prefs.contains("hostname") && prefs.getString("hostname", "") != "") {
			Cache.initializeDatabase(this)
			finish()
		}
	}
}