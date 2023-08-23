package eu.fliegendewurst.triliumdroid

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.icu.text.DateFormat
import android.icu.text.SimpleDateFormat
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.os.bundleOf
import androidx.preference.PreferenceManager
import org.json.JSONObject


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
			mainActivity.navigateTo(Cache.getNote(notePath.split("/").last())!!)
		}
	}

	@JavascriptInterface
	fun activateNewNote(notePath: String) {
	}

	@JavascriptInterface
	fun openTabWithNote(notePath: String, activate: Boolean) {
	}

	@JavascriptInterface
	fun openSplitWithNote(notePath: String, activate: Boolean) {
	}

	@JavascriptInterface
	fun runOnBackend(script: String, params: List<Object>) {
	}

	@JavascriptInterface
	fun searchForNotes(searchString: String) {
	}

	@JavascriptInterface
	fun searchForNote(searchString: String) {
	}

	@JavascriptInterface
	fun getNote(noteId: String) {
	}

	@JavascriptInterface
	fun getNotes(noteIds: List<String>, silentNotFoundError: Boolean = false) {
	}

	@JavascriptInterface
	fun reloadNotes(noteIds: List<String>) {
	}

	@JavascriptInterface
	fun getInstanceName(): String {
		return "mobile"
	}

	@JavascriptInterface
	fun formatDateISO(date: Object): String {
		return "TODO"
	}

	@JavascriptInterface
	fun parseDate(date: String): Object {
		return Object() // TODO
	}

	@JavascriptInterface
	fun showMessage(message: String) {
	}

	@JavascriptInterface
	fun showError(message: String) {
	}

	@JavascriptInterface
	fun triggerCommand(name: String, data: Object) {
	}

	@JavascriptInterface
	fun triggerEvent(name: String, data: Object) {
	}

	// this.createLink = linkService.createLink;

	// this.createNoteLink = linkService.createLink;

	@JavascriptInterface
	fun addTextToActiveContextEditor(text: String) {
	}

	@JavascriptInterface
	fun getActiveContextNote() {
	}

	@JavascriptInterface
	fun getActiveContextTextEditor() {
	}

	@JavascriptInterface
	fun getActiveContextCodeEditor() {
	}

	@JavascriptInterface
	fun getActiveNoteDetailWidget() {
	}

	@JavascriptInterface
	fun getActiveContextNotePath() {
	}

	@JavascriptInterface
	fun getComponentByEl(el: Object) {
	}

	@JavascriptInterface
	fun setupElementTooltip(el: Object) {
	}

	@JavascriptInterface
	fun protectNote(noteId: String, protect: Boolean) {
	}

	@JavascriptInterface
	fun protectSubTree(noteId: String, protect: Boolean) {
	}

	@JavascriptInterface
	fun getDayNote(day: String) {
	}

	@JavascriptInterface
	fun getWeekNote(date: String) {
	}

	@JavascriptInterface
	fun getMonthNote(month: String) {
	}

	@JavascriptInterface
	fun getYearNote(year: String) {
	}

	@JavascriptInterface
	fun setHoistedNoteId(noteId: String) {
	}

	@JavascriptInterface
	fun bindGlobalShortcut(keyboardShortcut: String, handler: Object, namespace: String) {
	}

	@JavascriptInterface
	fun waitUntilSynced() {
	}

	@JavascriptInterface
	fun refreshIncludedNote(includedNoteId: String) {
	}

	@JavascriptInterface
	fun randomString(length: Int): String {
		return "xx" // TODO
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