package eu.fliegendewurst.triliumdroid

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
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

		val readCertificate =
			registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
				if (result.resultCode != Activity.RESULT_OK) {
					return@registerForActivityResult
				}
				val data = result.data
				if (null != data) {
					val uris = mutableListOf<Uri>()
					if (null != data.clipData) {
						for (i in 0 until data.clipData!!.itemCount) {
							val uri = data.clipData!!.getItemAt(i).uri
							uris.add(uri)
						}
					} else if (data.data != null) {
						uris.add(data.data!!)
					}
					var mtlsCert = prefs.getString("mTLS_cert", null) ?: ""
					for (uri in uris) {
						contentResolver.openInputStream(
							uri
						)?.use { stream ->
							stream.bufferedReader().use {
								mtlsCert += it.readText()
							}
						}
					}
					val haveCert = mtlsCert.contains("-----BEGIN CERTIFICATE-----")
					val haveKey = mtlsCert.contains("-----BEGIN PRIVATE KEY-----")
					var error = ""
					if (!haveCert) {
						error += "No certificate found. "
					}
					if (!haveKey) {
						error += "No private key found."
					}
					if (error.isNotEmpty()) {
						AlertDialog.Builder(this)
							.setTitle("mTLS configuration error")
							.setMessage(error)
							.setPositiveButton(android.R.string.ok) { dialog, _ ->
								dialog.dismiss()
							}
							.setNegativeButton(android.R.string.cancel, null)
							.show()
					} else {
						prefs.edit().putString("mTLS_cert", mtlsCert).apply()
						ConnectionUtil.resetClient()
						binding.buttonConfigureMtls.isEnabled = false
						binding.buttonConfigureMtls2.isEnabled = true
					}
				}
			}

		val mtlsCert = prefs.getString("mTLS_cert", null)
		binding.buttonConfigureMtls.setOnClickListener {
			var chooseFile = Intent(Intent.ACTION_GET_CONTENT)
			chooseFile.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
			chooseFile.setType("*/*")
			chooseFile = Intent.createChooser(chooseFile, "Choose PEM-encoded certificate and key")
			readCertificate.launch(chooseFile)

			binding.buttonConfigureMtls.isEnabled = false
			binding.buttonConfigureMtls2.isEnabled = true
		}
		binding.buttonConfigureMtls2.isEnabled = mtlsCert == null
		binding.buttonConfigureMtls2.setOnClickListener {
			prefs.edit().remove("mTLS_cert").apply()
			binding.buttonConfigureMtls.isEnabled = true
			binding.buttonConfigureMtls2.isEnabled = false
		}
		binding.buttonConfigureMtls2.isEnabled = mtlsCert != null

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

		val primaryColorVal = prefs.getString("primaryColor", null)
		val primaryColor = if (primaryColorVal != null) {
			Color.parseColor(primaryColorVal)
		} else {
			application.resources.getColor(R.color.primary, null)
		}
		binding.buttonConfigurePrimaryColor.setTextColor(primaryColor)
		binding.buttonConfigurePrimaryColor.setOnClickListener {
			ColorPickerDialog.Builder(this)
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
				.show()
		}

		val secondaryColorVal = prefs.getString("secondaryColor", null)
		val secondaryColor = if (secondaryColorVal != null) {
			Color.parseColor(secondaryColorVal)
		} else {
			application.resources.getColor(R.color.secondary, null)
		}
		binding.buttonConfigureSecondaryColor.setTextColor(secondaryColor)
		binding.buttonConfigureSecondaryColor.setOnClickListener {
			ColorPickerDialog.Builder(this)
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
				.show()
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