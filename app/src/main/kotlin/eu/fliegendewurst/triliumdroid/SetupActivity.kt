package eu.fliegendewurst.triliumdroid

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import eu.fliegendewurst.triliumdroid.databinding.ActivitySetupBinding
import eu.fliegendewurst.triliumdroid.dialog.ConfigureFabsDialog
import java.io.File


class SetupActivity : AppCompatActivity() {
	private lateinit var binding: ActivitySetupBinding
	private lateinit var prefs: SharedPreferences

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivitySetupBinding.inflate(layoutInflater)
		prefs = PreferenceManager.getDefaultSharedPreferences(this)
		setContentView(binding.root)
		binding.server.setText(prefs.getString("hostname", ""))
		binding.password.setText(
			prefs.getString(
				"password",
				""
			)
		)
		ConfigureFabsDialog.init(prefs)
		setText()

		val exportLauncher =
			registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
				if (result.resultCode != Activity.RESULT_OK) {
					return@registerForActivityResult
				}
				contentResolver.openOutputStream(
					result.data?.data ?: return@registerForActivityResult
				)?.use { out ->
					val db = File(filesDir.parent, "databases/Document.db")
					db.inputStream().copyTo(out)
				}
			}

		binding.buttonConfigureFabs.setOnClickListener {
			ConfigureFabsDialog.showDialog(this, prefs) {
				setText()
			}
		}

		binding.buttonExportDatabase.setOnClickListener {
			val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
			intent.addCategory(Intent.CATEGORY_OPENABLE)
			intent.setType("application/vnd.sqlite3")
			intent.putExtra(Intent.EXTRA_TITLE, "document.db")
			exportLauncher.launch(intent)
		}

		binding.buttonNukeDatabase.setOnClickListener {
			AlertDialog.Builder(this)
				.setTitle("Delete database")
				.setMessage("Do you really want to delete all notes and related data?")
				.setIconAttribute(android.R.attr.alertDialogIcon)
				.setPositiveButton(
					android.R.string.ok
				) { _, _ ->
					Cache.nukeDatabase(this)
				}
				.setNegativeButton(android.R.string.cancel, null).show()
		}
	}

	private fun setText() {
		var x = ""
		val labels = resources.getStringArray(R.array.fabs)
		for (action in ConfigureFabsDialog.actions) {
			if (ConfigureFabsDialog.getPref(prefs, action)!!.first) {
				val label = labels[ConfigureFabsDialog.actions.indexOf(action)]
				x = label
			}
		}
		for (action in ConfigureFabsDialog.actions) {
			if (ConfigureFabsDialog.getPref(prefs, action)!!.second) {
				val label = labels[ConfigureFabsDialog.actions.indexOf(action)]
				x = "$x, $label"
			}
		}
		binding.inputFab.text = x
	}

	override fun onPause() {
		super.onPause()
		var server = binding.server.text.toString().trimEnd('/')
		val password = binding.password.text.toString()
		if (!(server.startsWith("http://") || server.startsWith("https://"))) {
			server = "http://${server}"
		}
		prefs.edit()
			.putString("hostname", server)
			.putString("password", password)
			.apply()
	}
}