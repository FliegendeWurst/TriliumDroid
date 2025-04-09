package eu.fliegendewurst.triliumdroid.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.fliegendewurst.triliumdroid.activity.main.HistoryItem
import eu.fliegendewurst.triliumdroid.service.Util
import eu.fliegendewurst.triliumdroid.widget.parseWidgetAction

object Preferences {
	// TODO: make this variable private
	lateinit var prefs: SharedPreferences
		private set

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
	private const val DATABASE_VERSION = "databaseVersion"

	fun init(applicationContext: Context) {
		prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
	}

	fun hostname(): String? = prefs.getString(HOSTNAME, null)
	fun password(): String? = prefs.getString(PASSWORD, null)
	fun instanceId(): String = prefs.getString(INSTANCE_ID, null).let {
		if (it == null) {
			setInstanceId("mobile" + Util.randomString(6))
			instanceId()
		} else {
			it
		}
	}

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

	/**
	 * Get the database version reported by the sync server.
	 */
	fun databaseVersion(): Int? = if (prefs.contains(DATABASE_VERSION)) {
		prefs.getInt(DATABASE_VERSION, 0)
	} else {
		null
	}

	fun widgetAction(appWidgetId: Int): HistoryItem? =
		parseWidgetAction(prefs.getString("widget_$appWidgetId", null))

	fun isLeftAction(action: String) = prefs.getBoolean("fab_${action}_left", false)
	fun isRightAction(action: String) = prefs.getBoolean("fab_${action}_right", false)

	fun setHostname(hostname: String) = prefs.edit { putString(HOSTNAME, hostname) }
	fun setPassword(password: String) = prefs.edit { putString(PASSWORD, password) }
	fun setInstanceId(newValue: String) = prefs.edit { putString(INSTANCE_ID, newValue) }
	fun setSyncVersion(newValue: Int) = prefs.edit { putInt(SYNC_VERSION, newValue) }
	fun setDatabaseVersion(newValue: Int) = prefs.edit { putInt(DATABASE_VERSION, newValue) }
	fun setLastReport(newValue: String) = prefs.edit { putString(LAST_REPORT, newValue) }
	fun setLastNote(noteId: String) = prefs.edit { putString(LAST_NOTE, noteId) }
	fun setSyncSSID(ssid: String) = prefs.edit { putString(SYNC_SSID, ssid) }
	fun setMTLS(alias: String) = prefs.edit { putString(MTLS_CERT, alias) }
	fun setDocumentSecret(newValue: String) =
		prefs.edit { putString(DOCUMENT_SECRET, newValue) }

	fun setDatabaseMigration(newValue: Int) = prefs.edit { putInt(DB_MIGRATION, newValue) }
	fun setWidgetAction(appWidgetId: Int, action: String) =
		prefs.edit { putString("widget_$appWidgetId", action) }

	fun clearMTLS() = prefs.edit { remove(MTLS_CERT) }
	fun clearSyncSSID() = prefs.edit { remove(SYNC_SSID) }

	fun clearSyncContext() = prefs.edit {
		remove(DOCUMENT_SECRET)
			.remove(SYNC_VERSION)
			.remove(INSTANCE_ID)
	}

	fun hasSyncContext() =
		listOf(DOCUMENT_SECRET, SYNC_VERSION, INSTANCE_ID).all { prefs.contains(it) }
}
