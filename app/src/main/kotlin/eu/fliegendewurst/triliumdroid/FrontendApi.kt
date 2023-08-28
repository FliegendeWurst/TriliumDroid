package eu.fliegendewurst.triliumdroid

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.icu.text.DateFormat
import android.icu.text.SimpleDateFormat
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.os.bundleOf
import androidx.preference.PreferenceManager
import eu.fliegendewurst.triliumdroid.service.DateNotes
import eu.fliegendewurst.triliumdroid.service.Util
import org.json.JSONObject


/**
 * Frontend javascript API object.
 * Due to WebView limitations all methods are synchronous, but awaiting their result is still
 * possible.
 *
 * New methods:
 * - isMobile(): always returns true
 * - registerAlarm(...): deliver a notification at the specified time
 *
 * TODO:
 * - create wrapper for properties (not supported by WebView, has to be hacked via script)
 * - create wrapper for addButtonToToolbar to offer identical API
 * - create fake objects for CKEditor / CodeMirror instances (if needed)
 * - implement remaining methods
 */
class FrontendApi(private val noteFragment: NoteFragment, private val context: Context) {
	private val mainActivity: MainActivity = noteFragment.requireActivity() as MainActivity

	companion object {
		const val TAG: String = "ApiInterface"

		@SuppressLint("SimpleDateFormat")
		val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
	}

	@JavascriptInterface
	fun isMobile(): Boolean {
		return true
	}

	@JavascriptInterface
	fun registerAlarm(tag: String, time: String, message: String, note: String) {
		if (!mainActivity.checkNotificationPermission()) {
			return
		}
		Log.i(TAG, "registerAlarm $tag $time $message $note")
		val context = noteFragment.requireContext()
		val alarmMgr = getSystemService(context, AlarmManager::class.java)!!
		val intent = Intent(context, AlarmReceiver::class.java)
		intent.putExtras(bundleOf(Pair("message", message), Pair("note", note)))
		val pendingIntent = PendingIntent.getBroadcast(
			context,
			0,
			intent,
			PendingIntent.FLAG_IMMUTABLE
		)
		val timeMs = df.parse(time).time
		alarmMgr.setExact(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)
	}

	@JavascriptInterface
	fun addButtonToToolbar(optsJson: String?) {
		if (optsJson == null) {
			Log.e(TAG, "addButtonToToolbar called with null!")
			return
		}
		val opts = JSONObject(optsJson)
		val title = opts.getString("title")
		val icon = if (opts.has("icon")) {
			opts.getString("icon")
		} else {
			null
		}
		val action = opts.getString("action")
		Log.i(TAG, "addButtonToToolbar $title $icon $action")
		val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
		if (prefs.contains("button$title")) {
			prefs.edit().putString("button$title", action).apply()
		} else {
			val countButton = prefs.getInt("countButton", 0) + 1
			prefs.edit()
				.putInt("countButton", countButton)
				.putString("button$title", action).apply()
		}
	}

	@JavascriptInterface
	fun getTodayNote(): FrontendNote {
		return FrontendNote(Cache.getNote("root")!!)
	}

	// TODO: properties (not supported by this interface)

	@JavascriptInterface
	fun activateNote(notePath: String) {
		mainActivity.handler.post {
			mainActivity.navigateToPath(notePath)
		}
	}

	@JavascriptInterface
	fun activateNewNote(notePath: String) {
		activateNote(notePath)
	}

	@JavascriptInterface
	fun openTabWithNote(notePath: String, activate: Boolean) {
		if (!activate) {
			return
		}
		activateNote(notePath)
	}

	@JavascriptInterface
	fun openSplitWithNote(notePath: String, activate: Boolean) {
		if (!activate) {
			return
		}
		activateNote(notePath)
	}

	@JavascriptInterface
	fun runOnBackend(script: String, params: List<Any>) {
	}

	@JavascriptInterface
	fun searchForNotes(searchString: String) {
	}

	@JavascriptInterface
	fun searchForNote(searchString: String) {
	}

	@JavascriptInterface
	fun getNote(noteId: String): FrontendNote? {
		return FrontendNote(Cache.getNoteWithContent(noteId) ?: return null)
	}

	@JavascriptInterface
	fun getNotes(noteIds: List<String>, silentNotFoundError: Boolean = false): List<FrontendNote> {
		// TODO: honor silentNotFoundError
		return noteIds.map { FrontendNote(Cache.getNoteWithContent(it) ?: return@map null) }
			.filterNotNull().toList()
	}

	@JavascriptInterface
	fun reloadNotes(noteIds: List<String>) {
		/* NOOP, the app has no frontend/backend distinction */
	}

	@JavascriptInterface
	fun getInstanceName(): String {
		return "mobile"
	}

	@JavascriptInterface
	fun formatDateISO(date: Any): String {
		return "TODO"
	}

	@JavascriptInterface
	fun parseDate(date: String): Any {
		return Object() // TODO
	}

	@JavascriptInterface
	fun showMessage(message: String) {
		mainActivity.handler.post {
			val t = Toast(mainActivity.applicationContext)
			t.setText(message)
			t.show()
		}
	}

	@JavascriptInterface
	fun showError(message: String) {
		mainActivity.handler.post {
			val t = Toast(mainActivity.applicationContext)
			t.setText(message)
			t.duration = Toast.LENGTH_LONG
			t.show()
		}
	}

	@JavascriptInterface
	fun triggerCommand(name: String, data: Any) {
	}

	@JavascriptInterface
	fun triggerEvent(name: String, data: Any) {
	}

	// this.createLink = linkService.createLink;

	// this.createNoteLink = linkService.createLink;

	@JavascriptInterface
	fun addTextToActiveContextEditor(text: String) {
		// TODO
	}

	@JavascriptInterface
	fun getActiveContextNote(): FrontendNote {
		return FrontendNote(mainActivity.getNoteLoaded())
	}

	@JavascriptInterface
	fun getActiveContextTextEditor(): Any? {
		return null /* no CKEditor here */
	}

	@JavascriptInterface
	fun getActiveContextCodeEditor(): Any? {
		return null /* no CodeMirror here */
	}

	@JavascriptInterface
	fun getActiveNoteDetailWidget(): Any? {
		return null /* no widgets here */
	}

	@JavascriptInterface
	fun getActiveContextNotePath() {
		// TODO
	}

	@JavascriptInterface
	fun getComponentByEl(el: Any): Any? {
		return null /* no components here */
	}

	@JavascriptInterface
	fun setupElementTooltip(el: Any) {
		/* NOOP: no jQuery here */
	}

	@JavascriptInterface
	fun protectNote(noteId: String, protect: Boolean) {
		// TODO
	}

	@JavascriptInterface
	fun protectSubTree(noteId: String, protect: Boolean) {
		// TODO
	}

	@JavascriptInterface
	fun getDayNote(day: String): FrontendNote? {
		return FrontendNote(DateNotes.getDayNote(day) ?: return null)
	}

	@JavascriptInterface
	fun getWeekNote(date: String): FrontendNote? {
		return FrontendNote(DateNotes.getWeekNote(date) ?: return null)
	}

	@JavascriptInterface
	fun getMonthNote(month: String): FrontendNote? {
		return FrontendNote(DateNotes.getMonthNote(month) ?: return null)
	}

	@JavascriptInterface
	fun getYearNote(year: String): FrontendNote? {
		return FrontendNote(DateNotes.getYearNote(year) ?: return null)
	}

	@JavascriptInterface
	fun setHoistedNoteId(noteId: String) {
		// TODO
	}

	@JavascriptInterface
	fun bindGlobalShortcut(keyboardShortcut: String, handler: Any, namespace: String) {
		// TODO: investigate usefulness
	}

	@JavascriptInterface
	fun waitUntilSynced() {
		/* NOOP: no frontend/backend distinction in app */
	}

	@JavascriptInterface
	fun refreshIncludedNote(includedNoteId: String) {
		// TODO: investigate once included notes are rendered
	}

	@JavascriptInterface
	fun randomString(length: Int): String {
		return Util.randomString(length)
	}

	@JavascriptInterface
	fun formatSize(size: Int): String {
		return "-1KB" // TODO
	}

	@JavascriptInterface
	fun formatNoteSize(size: Int): String {
		return formatSize(size)
	}

	@JavascriptInterface
	fun log(message: String) {
		Log.i(TAG, message)
	}
}