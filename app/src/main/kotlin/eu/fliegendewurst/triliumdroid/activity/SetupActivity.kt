package eu.fliegendewurst.triliumdroid.activity

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.security.KeyChain
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import eu.fliegendewurst.triliumdroid.Cache
import eu.fliegendewurst.triliumdroid.ConnectionUtil
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.databinding.ActivitySetupBinding
import eu.fliegendewurst.triliumdroid.dialog.ConfigureFabsDialog
import eu.fliegendewurst.triliumdroid.util.GetSSID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


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

		binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_vector)
		binding.toolbar.setNavigationOnClickListener {
			finish()
		}
		binding.toolbar.setTitle(R.string.action_settings)

		val s = ConnectionUtil.status()
		var status: Int? = R.string.status_unknown
		if (s.dbMismatch) {
			status = R.string.status_db_mismatch
		} else if (!s.loginSuccess) {
			status = R.string.status_login_fail
		} else if (!s.connectSuccess) {
			status = R.string.status_connection_fail
		} else if (s.lastSync != null) {
			status = null
			val deltaSeconds = (System.currentTimeMillis() - s.lastSync) / 1000
			binding.status.text =
				resources.getString(R.string.status_sync_success, (deltaSeconds / 60).toString())
		}
		if (status != null) {
			binding.status.setText(status)
		}

		binding.server.setText(prefs.getString("hostname", ""))
		binding.password.setText(
			prefs.getString(
				"password",
				""
			)
		)

		val mtlsCert = prefs.getString("mTLS_cert", null)
		binding.buttonConfigureMtls.setOnClickListener {
			KeyChain.choosePrivateKeyAlias(
				this@SetupActivity,
				{ alias ->
					Log.d(TAG, "received key alias $alias")
					if (alias == null) {
						return@choosePrivateKeyAlias
					}
					// test we can actually retrieve the key material
					lifecycleScope.launch {
						withContext(Dispatchers.IO) {
							KeyChain.getPrivateKey(applicationContext, alias)
							KeyChain.getCertificateChain(applicationContext, alias)
						}
						prefs.edit().putString("mTLS_cert", alias).apply()
						ConnectionUtil.resetClient(this@SetupActivity) {}
					}
				},
				null, // List of acceptable key types. null for any
				null,  // issuer, null for any
				null,  // TODO: host name of server requesting the cert, null if unavailable
				-1,  // TODO: port of server requesting the cert, -1 if unavailable
				null
			)
		}
		binding.buttonConfigureMtls.isEnabled = mtlsCert == null
		binding.buttonConfigureMtls2.setOnClickListener {
			prefs.edit().remove("mTLS_cert").apply()
			binding.buttonConfigureMtls.isEnabled = true
			binding.buttonConfigureMtls2.isEnabled = false
		}
		binding.buttonConfigureMtls2.isEnabled = mtlsCert != null

		val ssidLimitActive = prefs.contains("syncSSID")
		binding.buttonConfigureSsid.isEnabled = !ssidLimitActive
		binding.buttonConfigureSsidClear.isEnabled = ssidLimitActive
		binding.buttonConfigureSsid.setOnClickListener {
			GetSSID(this@SetupActivity) {
				lifecycleScope.launch {
					if (it == null) {
						return@launch
					}
					prefs.edit().putString("syncSSID", it).apply()
					binding.buttonConfigureSsid.isEnabled = false
					binding.buttonConfigureSsidClear.isEnabled = true
					ConnectionUtil.resetClient(this@SetupActivity) {}
				}
			}.getSSID()
		}
		binding.buttonConfigureSsidClear.setOnClickListener {
			prefs.edit().remove("syncSSID").apply()
			binding.buttonConfigureSsid.isEnabled = true
			binding.buttonConfigureSsidClear.isEnabled = false
			lifecycleScope.launch {
				ConnectionUtil.resetClient(this@SetupActivity) {}
			}
		}

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

		/*
		val primaryColorVal = prefs.getString("primaryColor", null)
		val primaryColor = if (primaryColorVal != null) {
			Color.parseColor(primaryColorVal)
		} else {
			application.resources.getColor(R.color.primary, null)
		}
		binding.buttonConfigurePrimaryColor.setTextColor(primaryColor)
		binding.buttonConfigurePrimaryColor.setOnClickListener {
			val picker = ColorPickerDialog.Builder(this)
				.setTitle("Primary background color")
				.setPreferenceName("primaryColorPicker")
				.setPositiveButton(getString(android.R.string.ok),
					ColorEnvelopeListener { envelope, _ ->
						prefs.edit().putString("primaryColor", "#${envelope.hexCode}").apply()
						binding.buttonConfigurePrimaryColor.setTextColor(envelope.color)
					})
				.setNegativeButton(
					getString(android.R.string.cancel)
				) { dialogInterface, i -> dialogInterface.dismiss() }
				.attachAlphaSlideBar(false)
				.attachBrightnessSlideBar(true)
				.setBottomSpace(12)
			picker.colorPickerView.setInitialColor(primaryColor)
			picker.show()
		}

		val secondaryColorVal = prefs.getString("secondaryColor", null)
		val secondaryColor = if (secondaryColorVal != null) {
			Color.parseColor(secondaryColorVal)
		} else {
			application.resources.getColor(R.color.secondary, null)
		}
		binding.buttonConfigureSecondaryColor.setTextColor(secondaryColor)
		binding.buttonConfigureSecondaryColor.setOnClickListener {
			val picker = ColorPickerDialog.Builder(this)
				.setTitle("Secondary background color")
				.setPreferenceName("secondaryColorPicker")
				.setPositiveButton(getString(android.R.string.ok),
					ColorEnvelopeListener { envelope, _ ->
						prefs.edit().putString("secondaryColor", "#${envelope.hexCode}").apply()
						binding.buttonConfigureSecondaryColor.setTextColor(envelope.color)
					})
				.setNegativeButton(
					getString(android.R.string.cancel)
				) { dialogInterface, i -> dialogInterface.dismiss() }
				.attachAlphaSlideBar(false)
				.attachBrightnessSlideBar(true)
				.setBottomSpace(12)
			picker.colorPickerView.setInitialColor(secondaryColor)
			picker.show()
		}
		 */

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
					binding.status.setText(R.string.status_unknown)
				}
				.setNegativeButton(android.R.string.cancel, null).show()
		}
	}

	private fun setText() {
		var x = ""
		val labels = resources.getStringArray(R.array.fabs)
		for (action in ConfigureFabsDialog.actions.keys) {
			if (ConfigureFabsDialog.getPref(prefs, action)!!.left) {
				val label = labels[ConfigureFabsDialog.actions.keys.indexOf(action)]
				x = label
			}
		}
		for (action in ConfigureFabsDialog.actions.keys) {
			if (ConfigureFabsDialog.getPref(prefs, action)!!.right) {
				val label = labels[ConfigureFabsDialog.actions.keys.indexOf(action)]
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
