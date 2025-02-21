package eu.fliegendewurst.triliumdroid.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import eu.fliegendewurst.triliumdroid.Cache
import eu.fliegendewurst.triliumdroid.databinding.ActivityWelcomeBinding
import eu.fliegendewurst.triliumdroid.util.Preferences
import kotlinx.coroutines.launch

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
		if (Preferences.hostname() != null) {
			lifecycleScope.launch {
				Cache.initializeDatabase(this@WelcomeActivity)
				finish()
			}
		}
	}
}
