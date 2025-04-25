package eu.fliegendewurst.triliumdroid.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.fliegendewurst.triliumdroid.activity.main.HistoryItem
import eu.fliegendewurst.triliumdroid.data.CanvasNoteViewport
import eu.fliegendewurst.triliumdroid.data.NoteId
import eu.fliegendewurst.triliumdroid.database.CacheDbHelper.Companion.MAX_MIGRATION
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
	private const val WEB_ASSETS_VERSION = "webAssetsVersion"
	private const val DOC_ASSETS_VERSION = "docAssetsVersion"
	private const val CSS_ASSETS_VERSION = "cssAssetsVersion"
	private const val READ_ONLY_MODE = "readOnlyMode"

	fun init(applicationContext: Context) {
		prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
		if (!prefs.contains(DB_MIGRATION) && !prefs.contains(HOSTNAME)) {
			// This is relevant if the app is uninstalled and reinstalled,
			// in which case the database may persist. We assume it doesn't
			// need fixups in that case.
			setDatabaseMigration(MAX_MIGRATION)
		}
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
	fun lastNote(): NoteId? = prefs.getString(LAST_NOTE, null).let {
		if (it != null) {
			NoteId(it)
		} else {
			null
		}
	}

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

	fun webAssetsVersion(): Int = prefs.getInt(WEB_ASSETS_VERSION, 0)
	fun docAssetsVersion(): Int = prefs.getInt(DOC_ASSETS_VERSION, 0)
	fun cssAssetsVersion(): Int = prefs.getInt(CSS_ASSETS_VERSION, 0)

	fun widgetAction(appWidgetId: Int): HistoryItem? =
		parseWidgetAction(prefs.getString("widget_$appWidgetId", null))

	fun canvasViewportOverride(id: NoteId): CanvasNoteViewport? {
		val keyX = "canvas_${id.rawId()}_x"
		val keyY = "canvas_${id.rawId()}_y"
		val keyZoom = "canvas_${id.rawId()}_zoom"
		if (!prefs.contains(keyX) || !prefs.contains(keyY) || !prefs.contains(keyZoom)) {
			return null
		}
		val x = prefs.getFloat(keyX, 0F)
		val y = prefs.getFloat(keyY, 0F)
		val zoom = prefs.getFloat(keyZoom, 0F)
		return CanvasNoteViewport(x, y, zoom)
	}

	fun readOnlyMode(): Boolean = prefs.getBoolean(READ_ONLY_MODE, false)

	fun isLeftAction(action: String) = prefs.getBoolean("fab_${action}_left", false)
	fun isRightAction(action: String) = prefs.getBoolean("fab_${action}_right", false)

	fun setHostname(hostname: String) = prefs.edit { putString(HOSTNAME, hostname) }
	fun setPassword(password: String) = prefs.edit { putString(PASSWORD, password) }
	fun setInstanceId(newValue: String) = prefs.edit { putString(INSTANCE_ID, newValue) }
	fun setSyncVersion(newValue: Int) = prefs.edit { putInt(SYNC_VERSION, newValue) }
	fun setDatabaseVersion(newValue: Int) = prefs.edit { putInt(DATABASE_VERSION, newValue) }
	fun setLastReport(newValue: String) = prefs.edit { putString(LAST_REPORT, newValue) }
	fun setLastNote(noteId: NoteId) = prefs.edit { putString(LAST_NOTE, noteId.rawId()) }
	fun setSyncSSID(ssid: String) = prefs.edit { putString(SYNC_SSID, ssid) }
	fun setMTLS(alias: String) = prefs.edit { putString(MTLS_CERT, alias) }
	fun setDocumentSecret(newValue: String) =
		prefs.edit { putString(DOCUMENT_SECRET, newValue) }

	fun setDatabaseMigration(newValue: Int) = prefs.edit { putInt(DB_MIGRATION, newValue) }
	fun setWidgetAction(appWidgetId: Int, action: String) =
		prefs.edit { putString("widget_$appWidgetId", action) }

	fun setCanvasViewportOverride(id: NoteId, view: CanvasNoteViewport) = prefs.edit {
		val keyX = "canvas_${id.rawId()}_x"
		val keyY = "canvas_${id.rawId()}_y"
		val keyZoom = "canvas_${id.rawId()}_zoom"
		putFloat(keyX, view.x)
		putFloat(keyY, view.y)
		putFloat(keyZoom, view.zoom)
	}

	fun setReadOnlyMode(readOnly: Boolean) = prefs.edit { putBoolean(READ_ONLY_MODE, readOnly) }

	fun setWebAssetsVersion(version: Int) = prefs.edit { putInt(WEB_ASSETS_VERSION, version) }
	fun setDocAssetsVersion(version: Int) = prefs.edit { putInt(DOC_ASSETS_VERSION, version) }
	fun setCssAssetsVersion(version: Int) = prefs.edit { putInt(CSS_ASSETS_VERSION, version) }

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
