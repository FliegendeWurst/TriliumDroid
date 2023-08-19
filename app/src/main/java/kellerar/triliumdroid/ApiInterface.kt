package kellerar.triliumdroid

import android.util.Log
import android.webkit.JavascriptInterface

class ApiInterface(private val noteFragment: NoteFragment) {
	companion object {
		const val TAG: String = "ApiInterface"
	}

	@JavascriptInterface
	fun isMobile(): Boolean {
		return true
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