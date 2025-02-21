package eu.fliegendewurst.triliumdroid.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

class GetSSID(private val context: AppCompatActivity, private val callback: (String?) -> Unit) {
	companion object {
		private const val TAG: String = "GetSSID"
	}

	private val wifiManager: WifiManager by lazy {
		context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
	}

	fun getSSID() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE)
				!= PackageManager.PERMISSION_GRANTED
			) {
				context.requestPermissions(
					arrayOf(
						android.Manifest.permission.ACCESS_FINE_LOCATION,
						android.Manifest.permission.ACCESS_WIFI_STATE
					),
					1
				)
				return
			}
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			retrieveSSIDWithConnectivityManager()
		} else {
			retrieveSSIDWithWifiManager()
		}
	}

	/**
	 * Retrieves the SSID using WifiManager for Android versions below API 31.
	 */
	@Suppress("DEPRECATION")
	private fun retrieveSSIDWithWifiManager() {
		val currentSSID = wifiManager.connectionInfo.ssid?.replace("\"", "")
		if (isFake(currentSSID)) {
			callback.invoke(null)
		} else {
			callback.invoke(currentSSID)
		}
	}

	/**
	 * Retrieves the SSID using ConnectivityManager for Android versions 31 and above.
	 */
	@RequiresApi(Build.VERSION_CODES.S)
	private fun retrieveSSIDWithConnectivityManager() {
		val request = NetworkRequest.Builder()
			.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
			.build()

		val connectivityManager =
			context.getSystemService(ConnectivityManager::class.java)
		connectivityManager.registerNetworkCallback(request, object : NetworkCallback(
			FLAG_INCLUDE_LOCATION_INFO
		) {
			override fun onCapabilitiesChanged(
				network: Network,
				networkCapabilities: NetworkCapabilities
			) {
				val info = networkCapabilities.transportInfo
				if (info is WifiInfo) {
					if (info.ssid == null) {
						Log.w(TAG, "null SSID in WifiInfo")
					}
					val ssid = info.ssid?.replace("\"", "")
					if (isFake(ssid)) {
						Log.w(TAG, "fake SSID $ssid")
						callback.invoke(null)
					} else {
						callback.invoke(ssid)
					}
				} else {
					Log.w(TAG, "TransportInfo not wifi: $info")
					callback.invoke(null)
				}
				connectivityManager.unregisterNetworkCallback(this)
			}
		})
	}

	private fun isFake(ssid: String?): Boolean {
		return ssid == null || ssid == "<unknown ssid>" || ssid == "02:00:00:00:00:00"
	}
}
