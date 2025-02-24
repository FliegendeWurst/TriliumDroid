package eu.fliegendewurst.triliumdroid.dialog

import android.security.KeyChain
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import eu.fliegendewurst.triliumdroid.ConnectionUtil
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.activity.WelcomeActivity
import eu.fliegendewurst.triliumdroid.util.GetSSID
import eu.fliegendewurst.triliumdroid.util.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ConfigureSyncDialog {
	private const val TAG = "ConfigureSyncDialog"

	fun showDialog(activity: AppCompatActivity) {
		var server: EditText? = null
		var password: EditText? = null
		val dialog = AlertDialog.Builder(activity)
			.setTitle(R.string.settings_sync_server_header)
			.setView(R.layout.dialog_configure_sync)
			.setPositiveButton(android.R.string.ok) { dialog, _ ->
				done(activity, server!!.text.toString(), password!!.text.toString())
				dialog.dismiss()
			}
			.create()
		dialog.show()

		server = dialog.findViewById(R.id.server)!!
		password = dialog.findViewById(R.id.password)!!
		val buttonConfigureMtls = dialog.findViewById<Button>(R.id.button_configure_mtls)!!
		val buttonConfigureMtls2 = dialog.findViewById<Button>(R.id.button_configure_mtls_2)!!
		val buttonConfigureSsid = dialog.findViewById<Button>(R.id.button_configure_ssid)!!
		val buttonConfigureSsidClear =
			dialog.findViewById<Button>(R.id.button_configure_ssid_clear)!!

		server.setText(Preferences.hostname() ?: "")
		password.setText(Preferences.password() ?: "")

		val mtlsCert = Preferences.mTLS()
		buttonConfigureMtls.setOnClickListener {
			KeyChain.choosePrivateKeyAlias(
				activity,
				{ alias ->
					Log.d(TAG, "received key alias $alias")
					if (alias == null) {
						return@choosePrivateKeyAlias
					}
					// test we can actually retrieve the key material
					val applicationContext = activity.applicationContext
					activity.lifecycleScope.launch {
						withContext(Dispatchers.IO) {
							KeyChain.getPrivateKey(applicationContext, alias)
							KeyChain.getCertificateChain(applicationContext, alias)
						}
						Preferences.setMTLS(alias)
						ConnectionUtil.resetClient(activity) {}
					}
				},
				null, // List of acceptable key types. null for any
				null,  // issuer, null for any
				null,  // TODO: host name of server requesting the cert, null if unavailable
				-1,  // TODO: port of server requesting the cert, -1 if unavailable
				null
			)
		}
		buttonConfigureMtls.isEnabled = mtlsCert == null
		buttonConfigureMtls2.setOnClickListener {
			Preferences.clearMTLS()
			buttonConfigureMtls.isEnabled = true
			buttonConfigureMtls2.isEnabled = false
		}
		buttonConfigureMtls2.isEnabled = mtlsCert != null

		val ssidLimitActive = Preferences.syncSSID() != null
		buttonConfigureSsid.isEnabled = !ssidLimitActive
		buttonConfigureSsidClear.isEnabled = ssidLimitActive
		buttonConfigureSsid.setOnClickListener {
			GetSSID(activity) {
				activity.lifecycleScope.launch {
					if (it == null) {
						return@launch
					}
					Preferences.setSyncSSID(it)
					buttonConfigureSsid.isEnabled = false
					buttonConfigureSsidClear.isEnabled = true
					ConnectionUtil.resetClient(activity) {}
				}
			}.getSSID()
		}
		buttonConfigureSsidClear.setOnClickListener {
			Preferences.clearSyncSSID()
			buttonConfigureSsid.isEnabled = true
			buttonConfigureSsidClear.isEnabled = false
			activity.lifecycleScope.launch {
				ConnectionUtil.resetClient(activity) {}
			}
		}
	}

	private fun done(
		activity: AppCompatActivity,
		serverInput: String,
		password: String
	) {
		var server = serverInput.trimEnd('/')
		if (!(server.startsWith("http://") || server.startsWith("https://"))) {
			server = "http://${server}"
		}
		Preferences.setHostname(server)
		Preferences.setPassword(password)
		activity.lifecycleScope.launch {
			ConnectionUtil.resetClient(activity) {
				if (activity is WelcomeActivity) {
					activity.finish()
				}
			}
		}
	}
}
