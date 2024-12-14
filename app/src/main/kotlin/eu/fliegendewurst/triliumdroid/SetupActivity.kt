package eu.fliegendewurst.triliumdroid

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import eu.fliegendewurst.triliumdroid.databinding.ActivitySetupBinding
import eu.fliegendewurst.triliumdroid.dialog.ConfigureFabsDialog

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
		binding.buttonConfigureFabs.setOnClickListener {
			ConfigureFabsDialog.showDialog(this, prefs) {
				setText()
			}
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
		val server = binding.server.text.toString().trimEnd('/')
		val password = binding.password.text.toString()
		prefs.edit()
			.putString("hostname", server)
			.putString("password", password)
			.apply()
	}
}