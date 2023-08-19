package kellerar.triliumdroid

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.icu.text.DateFormat
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.os.bundleOf


class ApiInterface(private val noteFragment: NoteFragment) {
	private val mainActivity: MainActivity = noteFragment.requireActivity() as MainActivity

	@SuppressLint("SimpleDateFormat")
	companion object {
		const val TAG: String = "ApiInterface"
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

	// TODO: properties (not supported by this interface)

	@JavascriptInterface
	fun activateNote(notePath: String) {
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
	fun addButtonToToolbar(opts: Object) {
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
	fun getTodayNote() {
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