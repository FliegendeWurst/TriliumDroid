package eu.fliegendewurst.triliumdroid.util

import android.content.SharedPreferences

object Preferences {
	// TODO: make this variable private
	lateinit var prefs: SharedPreferences

	private const val HOSTNAME = "hostname"
	private const val PASSWORD = "password"
	private const val INSTANCE_ID = "instanceId"
	private const val DOCUMENT_SECRET = "documentSecret"
	private const val SYNC_SSID = "syncSSID"
	private const val MTLS_CERT = "mTLS_cert"
	private const val LAST_REPORT = "LastReport"
	private const val LAST_NOTE = "LastNote"
	private const val SYNC_VERSION = "syncVersion"
	private const val DB_MIGRATION = "dbMigration"

	fun hostname(): String? = prefs.getString(HOSTNAME, null)
	fun password(): String? = prefs.getString(PASSWORD, null)
	fun instanceId(): String? = prefs.getString(INSTANCE_ID, null)
	fun documentSecret(): String? = prefs.getString(DOCUMENT_SECRET, null)
	fun syncSSID(): String? = prefs.getString(SYNC_SSID, null)
	fun mTLS(): String? = prefs.getString(MTLS_CERT, null)
	fun lastReport(): String? = prefs.getString(LAST_REPORT, null)
	fun lastNote(): String? = prefs.getString(LAST_NOTE, null)
	fun databaseMigration(): Int = prefs.getInt(DB_MIGRATION, 0)
	fun syncVersion(): Int? = if (prefs.contains(SYNC_VERSION)) {
		prefs.getInt(SYNC_VERSION, 0)
	} else {
		null
	}

	fun isLeftAction(action: String) = prefs.getBoolean("fab_${action}_left", false)
	fun isRightAction(action: String) = prefs.getBoolean("fab_${action}_right", false)

	fun setHostname(hostname: String) = prefs.edit().putString(HOSTNAME, hostname).apply()
	fun setPassword(password: String) = prefs.edit().putString(PASSWORD, password).apply()
	fun setInstanceId(newValue: String) = prefs.edit().putString(INSTANCE_ID, newValue).apply()
	fun setSyncVersion(newValue: Int) = prefs.edit().putInt(SYNC_VERSION, newValue).apply()
	fun setLastReport(newValue: String) = prefs.edit().putString(LAST_REPORT, newValue).apply()
	fun setLastNote(noteId: String) = prefs.edit().putString(LAST_NOTE, noteId).apply()
	fun setSyncSSID(ssid: String) = prefs.edit().putString(SYNC_SSID, ssid).apply()
	fun setMTLS(alias: String) = prefs.edit().putString(MTLS_CERT, alias).apply()
	fun setDocumentSecret(newValue: String) =
		prefs.edit().putString(DOCUMENT_SECRET, newValue).apply()

	fun setDatabaseMigration(newValue: Int) = prefs.edit().putInt(DB_MIGRATION, newValue).apply()

	fun clearMTLS() = prefs.edit().remove(MTLS_CERT).apply()
	fun clearSyncSSID() = prefs.edit().remove(SYNC_SSID).apply()

	fun clearSyncContext() = prefs.edit()
		.remove(DOCUMENT_SECRET)
		.remove(SYNC_VERSION)
		.remove(INSTANCE_ID)
		.apply()
}
