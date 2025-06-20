package eu.fliegendewurst.triliumdroid.activity

import android.app.UiModeManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.database.DB
import eu.fliegendewurst.triliumdroid.databinding.ActivitySetupBinding
import eu.fliegendewurst.triliumdroid.dialog.ConfigureFabsDialog
import eu.fliegendewurst.triliumdroid.dialog.ConfigureSyncDialog
import eu.fliegendewurst.triliumdroid.dialog.YesNoDialog
import eu.fliegendewurst.triliumdroid.service.Option
import eu.fliegendewurst.triliumdroid.sync.ConnectionUtil
import eu.fliegendewurst.triliumdroid.util.Preferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File


class SetupActivity : AppCompatActivity() {
	companion object {
		private const val TAG: String = "SetupActivity"
	}

	private lateinit var binding: ActivitySetupBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivitySetupBinding.inflate(layoutInflater)
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

		binding.buttonConfigureSync.setOnClickListener {
			ConfigureSyncDialog.showDialog(this) {}
		}

		binding.buttonChangeLanguage.setOnClickListener {
			val appLocale = LocaleListCompat.forLanguageTags("en-US,de")
			val localeStrings = mutableListOf<CharSequence>()
			val locales = AppCompatDelegate.getApplicationLocales()
			var idx = 0
			for (i in 0 until appLocale.size()) {
				if (!locales.isEmpty && locales.get(0) == appLocale[i]) {
					idx = i
				}
				localeStrings.add(appLocale[i]!!.displayLanguage)
			}
			AlertDialog.Builder(this)
				.setSingleChoiceItems(localeStrings.toTypedArray(), idx) { dialog, n ->
					val locale = appLocale[n]!!
					lifecycleScope.launch {
						AppCompatDelegate.setApplicationLocales(
							LocaleListCompat.create(locale)
						)
					}
					dialog.dismiss()
				}
				.setNegativeButton(android.R.string.cancel, null)
				.setTitle(R.string.label_set_ui_language)
				.show()
		}
		binding.buttonChangeDayNight.setOnClickListener {
			AlertDialog.Builder(this)
				.setSingleChoiceItems(
					arrayOf(
						resources.getString(R.string.theme_day),
						resources.getString(R.string.theme_night),
						resources.getString(R.string.theme_auto)
					), -1
				) { dialog, n ->
					lifecycleScope.launch {
						val newValue = when (n) {
							0 -> {
								UiModeManager.MODE_NIGHT_NO
							}

							1 -> {
								UiModeManager.MODE_NIGHT_YES
							}

							2 -> {
								UiModeManager.MODE_NIGHT_AUTO
							}

							else -> null
						}
						if (newValue != null) {
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
								this@SetupActivity.getSystemService(UiModeManager::class.java)
									.setApplicationNightMode(newValue)
							} else {
								AppCompatDelegate.setDefaultNightMode(newValue)
							}
						}
					}
					dialog.dismiss()
				}
				.setNegativeButton(android.R.string.cancel, null)
				.setTitle(R.string.label_set_ui_language)
				.show()
		}
		binding.buttonChangeTextSize.setOnClickListener {
			AlertDialog.Builder(this)
				.setSingleChoiceItems(
					arrayOf(
						resources.getString(R.string.textsize_auto),
						resources.getString(R.string.textsize_manual),
					), -1
				) { dialog, n ->
					lifecycleScope.launch {
						when (n) {
							0 -> {
								Preferences.setTextSize(null)
							}

							1 -> {
								val dialog = AlertDialog.Builder(this@SetupActivity)
								dialog.setTitle(R.string.textsize_manual_title)
								val input = EditText(this@SetupActivity)
								input.inputType = InputType.TYPE_CLASS_NUMBER
								val previous = Preferences.textSize()
								if (previous != -1) {
									input.setText(previous.toString())
								}
								dialog.setView(input)
									.setPositiveButton(android.R.string.ok) { dialog, which ->
										Preferences.setTextSize(input.text.toString().toIntOrNull())
										dialog.dismiss()
									}
									.setNegativeButton(android.R.string.cancel, null)
								dialog.show()
							}
						}
					}
					dialog.dismiss()
				}
				.setNegativeButton(android.R.string.cancel, null)
				.setTitle(R.string.label_set_text_size)
				.show()
		}

		setText()

		val exportLauncher =
			registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
				if (result.resultCode != RESULT_OK) {
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
			ConfigureFabsDialog.showDialog(this) {
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

		runBlocking {
			binding.revisionInterval.setText(Option.revisionInterval().toString())
		}

		binding.buttonExportDatabase.setOnClickListener {
			val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
			intent.addCategory(Intent.CATEGORY_OPENABLE)
			intent.setType("application/vnd.sqlite3")
			intent.putExtra(Intent.EXTRA_TITLE, "document.db")
			exportLauncher.launch(intent)
		}

		binding.buttonNukeDatabase.setOnClickListener {
			YesNoDialog.show(this, R.string.title_delete_database, R.string.text_nuke_database) {
				DB.nukeDatabase(this)
				binding.status.setText(R.string.status_unknown)
			}
		}

		binding.checkboxReadOnly.isChecked = Preferences.readOnlyMode()
	}

	override fun onStop() {
		super.onStop()

		runBlocking {
			val newInterval = binding.revisionInterval.text.toString().toIntOrNull()
			if (newInterval == 528491) {
				throw IllegalStateException("don't set the interval to 528491, that value triggers a crash")
			}
			if (newInterval != null && newInterval != Option.revisionInterval()) {
				Option.revisionIntervalUpdate(newInterval)
			}
		}

		val newReadOnly = binding.checkboxReadOnly.isChecked
		if (newReadOnly != Preferences.readOnlyMode()) {
			Toast.makeText(this, R.string.hint_read_only_restart, Toast.LENGTH_LONG).show()
		}
		Preferences.setReadOnlyMode(newReadOnly)
	}

	private fun setText() {
		var x = ""
		val labels = resources.getStringArray(R.array.fabs)
		for (action in ConfigureFabsDialog.actions.keys) {
			if (ConfigureFabsDialog.getPref(action)!!.left) {
				val label = labels[ConfigureFabsDialog.actions.keys.indexOf(action)]
				x = label
			}
		}
		for (action in ConfigureFabsDialog.actions.keys) {
			if (ConfigureFabsDialog.getPref(action)!!.right) {
				val label = labels[ConfigureFabsDialog.actions.keys.indexOf(action)]
				x = "$x, $label"
			}
		}
		binding.inputFab.text = x
	}
}
