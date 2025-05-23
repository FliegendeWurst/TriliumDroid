package eu.fliegendewurst.triliumdroid.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import eu.fliegendewurst.triliumdroid.database.DB
import eu.fliegendewurst.triliumdroid.databinding.ActivityWelcomeBinding
import eu.fliegendewurst.triliumdroid.dialog.ConfigureSyncDialog
import eu.fliegendewurst.triliumdroid.sync.ConnectionUtil
import eu.fliegendewurst.triliumdroid.util.Preferences
import kotlinx.coroutines.launch

class WelcomeActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val binding = ActivityWelcomeBinding.inflate(layoutInflater)
		setContentView(binding.root)
		binding.buttonSetupSync.setOnClickListener {
			ConfigureSyncDialog.showDialog(this) {
				lifecycleScope.launch {
					ConnectionUtil.resetClient(this@WelcomeActivity) {
						finish()
					}
				}
			}
		}
	}

	override fun onStart() {
		super.onStart()
		if (Preferences.hostname() != null) {
			lifecycleScope.launch {
				DB.initializeDatabase(this@WelcomeActivity)
				finish()
			}
		}
	}
}
