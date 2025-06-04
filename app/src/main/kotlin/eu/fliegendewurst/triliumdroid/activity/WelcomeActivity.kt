package eu.fliegendewurst.triliumdroid.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import eu.fliegendewurst.triliumdroid.database.DB
import eu.fliegendewurst.triliumdroid.databinding.ActivityWelcomeBinding
import eu.fliegendewurst.triliumdroid.dialog.ConfigureSyncDialog
import eu.fliegendewurst.triliumdroid.sync.ConnectionUtil
import eu.fliegendewurst.triliumdroid.util.Preferences
import kotlinx.coroutines.launch
import java.io.File

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
		val importLauncher =
			registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
				if (result.resultCode != RESULT_OK) {
					return@registerForActivityResult
				}
				contentResolver.openInputStream(
					result.data?.data ?: return@registerForActivityResult
				)?.use { input ->
					DB.nukeDatabase(this)
					File(filesDir.parent, "databases").mkdirs()
					val db = File(filesDir.parent, "databases/Document.db")
					db.outputStream().use { out ->
						out.buffered().use {
							input.copyTo(out)
						}
					}
					DB.skipNextMigration = true
					finish()
				}
			}
		binding.buttonImportDatabase.setOnClickListener {
			val intent = Intent(Intent.ACTION_GET_CONTENT)
			intent.addCategory(Intent.CATEGORY_OPENABLE)
			intent.setType("*/*")
			importLauncher.launch(intent)
		}
	}

	override fun onStart() {
		super.onStart()
		if (Preferences.databaseConfigured(this)) {
			lifecycleScope.launch {
				DB.initializeDatabase(this@WelcomeActivity)
				finish()
			}
		}
	}
}
