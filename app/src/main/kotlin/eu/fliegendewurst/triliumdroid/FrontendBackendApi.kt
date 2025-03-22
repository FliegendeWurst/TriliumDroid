package eu.fliegendewurst.triliumdroid

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.icu.text.DateFormat
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.os.Handler
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.os.bundleOf
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.data.Attribute
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.database.Cache
import eu.fliegendewurst.triliumdroid.database.Notes
import eu.fliegendewurst.triliumdroid.fragment.NoteFragment
import eu.fliegendewurst.triliumdroid.service.DateNotes
import eu.fliegendewurst.triliumdroid.service.Util
import eu.fliegendewurst.triliumdroid.util.Preferences
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


// based on:
// https://github.com/zadam/trilium/blob/master/src/public/app/services/frontend_script_api.js d3730ac
// https://github.com/zadam/trilium/blob/master/src/services/backend_script_api.js d3730ac

/**
 * Frontend and backend Javascript API object.
 *
 * New methods:
 * - [isMobile]: always returns true
 * - [registerAlarm]: deliver a notification at the specified time
 *
 * Internal technical considerations:
 * - All methods must be synchronous. It is possible to await plain values.
 * - All methods must return strings. They must be wrapped when executing scripts.
 *
 * TODO:
 * - create wrapper for properties (not supported by WebView, has to be hacked via script)
 * - create wrapper for addButtonToToolbar to offer identical API
 * - create fake objects for CKEditor / CodeMirror instances (if needed)
 * - implement remaining methods
 */
class FrontendBackendApi(
	private val noteFragment: NoteFragment,
	private val context: Context,
	private val handler: Handler
) {
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

	/**
	 * Open a date selector. Date chosen by user will be supplied to the callback.
	 */
	@JavascriptInterface
	fun openDateSelector(callback: String) {
		val c: Calendar = Calendar.getInstance()
		val year = c.get(Calendar.YEAR)
		val month = c.get(Calendar.MONTH)
		val day = c.get(Calendar.DAY_OF_MONTH)

		handler.post {
			val dpd = DatePickerDialog(
				context,
				{ _, year, month, dayOfMonth ->
					(noteFragment.activity as MainActivity).runScript(
						noteFragment,
						"($callback)('$year-${
							(month + 1).toString().padStart(2, '0')
						}-${dayOfMonth.toString().padStart(2, '0')}')"
					)
				},
				year, month, day
			)
			dpd.show()
		}
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
		try {
			alarmMgr.setExact(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)
		} catch (e: SecurityException) {
			alarmMgr.set(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)
		}
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
		if (Preferences.prefs.contains("button$title")) {
			Preferences.prefs.edit().putString("button$title", action).apply()
		} else {
			val countButton = Preferences.prefs.getInt("countButton", 0) + 1
			Preferences.prefs.edit()
				.putInt("countButton", countButton)
				.putString("button$title", action).apply()
		}
		// TODO: this should be the method below
	}

	/**
	 * Creates a new launcher to the launchbar. If the launcher (id) already exists, it will be updated.
	 *
	 * @method
	 * @param {object} opts
	 * @param {string} opts.id - id of the launcher, only alphanumeric at least 6 characters long
	 * @param {"note" | "script" | "customWidget"} opts.type - one of
	 *                          * "note" - activating the launcher will navigate to the target note (specified in targetNoteId param)
	 *                          * "script" -  activating the launcher will execute the script (specified in scriptNoteId param)
	 *                          * "customWidget" - the launcher will be rendered with a custom widget (specified in widgetNoteId param)
	 * @param {string} opts.title
	 * @param {boolean} [opts.isVisible=false] - if true, will be created in the "Visible launchers", otherwise in "Available launchers"
	 * @param {string} [opts.icon] - name of the boxicon to be used (e.g. "bx-time")
	 * @param {string} [opts.keyboardShortcut] - will activate the target note/script upon pressing, e.g. "ctrl+e"
	 * @param {string} [opts.targetNoteId] - for type "note"
	 * @param {string} [opts.scriptNoteId] - for type "script"
	 * @param {string} [opts.widgetNoteId] - for type "customWidget"
	 * @returns {{note: BNote}}
	 */
	fun createOrUpdateLauncher(opts: JSONObject) {

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

	/**
	 * Create text note. See also createNewNote() for more options.
	 *
	 * @param parentNoteId
	 * @param title
	 * @param content
	 * @returns {{note: BNote, branch: BBranch}} - object having "note" and "branch" keys representing respective objects
	 */
	fun createTextNote(parentNoteId: String, title: String, content: String?): JSONObject {
		TODO("xxx")
	}

	/**
	 * Create data note - data in this context means object serializable to JSON. Created note will be of type 'code' and
	 * JSON MIME type. See also createNewNote() for more options.
	 *
	 * @method
	 * @param parentNoteId
	 * @param title
	 * @param content
	 * @returns {{note: BNote, branch: BBranch}} object having "note" and "branch" keys representing respective objects
	 */
	fun createDataNote(parentNoteId: String, title: String, content: JSONObject): JSONObject {
		TODO("xxx")
		// 		parentNoteId,
		//		title,
		//		content: JSON.stringify(content, null, '\t'),
		//		type: 'code',
		//		mime: 'application/json'
		//	});
	}

	/**
	 * @method
	 *
	 * @param params
	 * @param {string} params.parentNoteId
	 * @param {string} params.title
	 * @param {string|Buffer} params.content
	 * @param {NoteType} params.type - text, code, file, image, search, book, relationMap, canvas
	 * @param {string} [params.mime] - value is derived from default mimes for type
	 * @param {boolean} [params.isProtected=false]
	 * @param {boolean} [params.isExpanded=false]
	 * @param {string} [params.prefix='']
	 * @param {int} [params.notePosition] - default is last existing notePosition in a parent + 10
	 * @returns {{note: BNote, branch: BBranch}} object contains newly created entities note and branch
	 */
	fun createNewNote(params: JSONObject): JSONObject {
		TODO("xxx")
	}

	/**
	 * Get a note by its ID.
	 */
	fun getNote(noteId: String): Note? {
		return runBlocking { Notes.getNoteWithContent(noteId) }
	}

	/**
	 * @suppress
	 */
	@JavascriptInterface
	fun getNoteInternal(noteId: String): JSONObject? {
		val note = getNote(noteId) ?: return null
		return encodeNote(note)
	}

	@JavascriptInterface
	fun getNotes(noteIds: List<String>, silentNotFoundError: Boolean = false): List<FrontendNote> {
		// TODO: honor silentNotFoundError
		return noteIds.map {
			FrontendNote(runBlocking { Notes.getNoteWithContent(it) } ?: return@map null)
		}
			.filterNotNull().toList()
	}

	fun getNotesWithLabel(name: String, value: String?): List<Note> {
		TODO("xxx")
	}

	fun getNoteWithLabel(name: String, value: String?): Note? {
		TODO("xxx")
	}

	@JavascriptInterface
	fun reloadNotes(noteIds: List<String>) {
		/* NOOP, the app has no frontend/backend distinction */
	}

	@JavascriptInterface
	fun getBranch(branchId: String): Branch? {
		TODO("xxx")
	}

	/**
	 * If there's no branch between note and parent note, create one. Otherwise, do nothing. Returns the new or existing branch.
	 *
	 * @param noteId
	 * @param parentNoteId
	 * @param prefix - if branch is created between note and parent note, set this prefix
	 */
	fun ensureNoteIsPresentInParent(noteId: String, parentNoteId: String): Branch? {
		TODO("xxx")
	}

	/**
	 * If there's a branch between note and parent note, remove it. Otherwise, do nothing.
	 *
	 * @param noteId
	 * @param parentNoteId
	 */
	fun ensureNoteIsAbsentFromParent(noteId: String, parentNoteId: String) {
		TODO("xxx")
	}

	/**
	 * Based on the value, either create or remove branch between note and parent note.
	 *
	 * @method
	 * @param {boolean} present - true if we want the branch to exist, false if we want it gone
	 * @param {string} noteId
	 * @param {string} parentNoteId
	 * @param {string} prefix - if branch is created between note and parent note, set this prefix
	 * @returns {void}
	 */
	fun toggleNoteInParent(
		present: Boolean,
		noteId: String,
		parentNoteId: String,
		prefix: String?
	) {
		TODO("Xxx")
	}

	/**
	 * This method finds note by its noteId and prefix and either sets it to the given parentNoteId
	 * or removes the branch (if parentNoteId is not given).
	 *
	 * This method looks similar to toggleNoteInParent() but differs because we're looking up branch by prefix.
	 *
	 * @method
	 * @deprecated this method is pretty confusing and serves specialized purpose only
	 * @param {string} noteId
	 * @param {string} prefix
	 * @param {string|null} parentNoteId
	 * @returns {void}
	 */
	fun setNoteToParent(noteId: String, prefix: String?, parentNoteId: String?) {
		TODO("xxx") // I'm confused by the docs, will this leave notes dangling??!
	}

	@JavascriptInterface
	fun getAttribute(attributeId: String): Attribute? {
		TODO("xxx")
	}

	@JavascriptInterface
	fun getInstanceName(): String {
		return Preferences.instanceId()
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
	fun getActiveContextNote(): FrontendNote? {
		return FrontendNote(mainActivity.getNoteLoaded() ?: return null)
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

	/**
	 * Returns today's day note. If such note doesn't exist, it is created.
	 *
	 * @method
	 * @param {BNote} [rootNote] - specify calendar root note, normally leave empty to use the default calendar
	 * @returns {BNote|null}
	 */
	@JavascriptInterface
	fun getTodayNote(rootNote: String?): FrontendNote? {
		return FrontendNote(runBlocking { DateNotes.getTodayNote() } ?: return null)
	}

	/**
	 * Returns day note for given date. If such note doesn't exist, it is created.
	 *
	 * @param day date in YYYY-MM-DD format
	 * @param rootNote specify calendar root note, normally leave empty to use the default calendar
	 */
	fun getDayNote(day: String, rootNote: String?): Note? {
		// TODO: root note handling
		return runBlocking { DateNotes.getDayNote(day) }
	}

	/**
	 * @suppress
	 */
	@JavascriptInterface
	fun getDayNoteInternal(day: String, rootNote: String?): String? {
		return encodeNote(getDayNote(day, rootNote))?.toString()
	}

	/**
	 * Returns note for the first date of the week of the given date.
	 *
	 * @method
	 * @param {string} date in YYYY-MM-DD format
	 * @param {object} [options]
	 * @param {string} [options.startOfTheWeek=monday] - either "monday" (default) or "sunday"
	 * @param {BNote} [rootNote] - specify calendar root note, normally leave empty to use the default calendar
	 * @returns {BNote|null}
	 */
	@JavascriptInterface
	fun getWeekNote(date: String, options: JSONObject?, rootNote: String?): FrontendNote? {
		return FrontendNote(DateNotes.getWeekNote(date) ?: return null)
	}

	/**
	 * Returns month note for given date. If such note doesn't exist, it is created.
	 *
	 * @method
	 * @param {string} date in YYYY-MM format
	 * @param {BNote} [rootNote] - specify calendar root note, normally leave empty to use the default calendar
	 * @returns {BNote|null}
	 */
	@JavascriptInterface
	fun getMonthNote(month: String, rootNote: String?): FrontendNote? {
		return FrontendNote(runBlocking { DateNotes.getMonthNote(month) } ?: return null)
	}

	/**
	 * Returns year note for given year. If such note doesn't exist, it is created.
	 *
	 * @method
	 * @param {string} year in YYYY format
	 * @param {BNote} [rootNote] - specify calendar root note, normally leave empty to use the default calendar
	 * @returns {BNote|null}
	 */
	@JavascriptInterface
	fun getYearNote(year: String, rootNote: String?): FrontendNote? {
		return FrontendNote(runBlocking { DateNotes.getYearNote(year) } ?: return null)
	}

	/**
	 * Returns root note of the calendar.
	 *
	 * @returns {BNote|null}
	 */
	fun getRootCalendarNote(): Note? {
		return runBlocking { DateNotes.getCalendarRoot() }
	}

	@JavascriptInterface
	fun setHoistedNoteId(noteId: String) {
		// TODO
	}

	/**
	 * Sort child notes of a given note.
	 *
	 * @method
	 * @param {string} parentNoteId - this note's child notes will be sorted
	 * @param {object} [sortConfig]
	 * @param {string} [sortConfig.sortBy=title] - 'title', 'dateCreated', 'dateModified' or a label name
	 *                                See {@link https://github.com/zadam/trilium/wiki/Sorting} for details.
	 * @param {boolean} [sortConfig.reverse=false]
	 * @param {boolean} [sortConfig.foldersFirst=false]
	 * @returns {void}
	 */
	fun sortNotes(parentNoteId: String, sortConfig: JSONObject) {
		TODO("xxx")
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

	// this.startNote = apiParams.startNote; // TODO: executing script note
	// this.currentNote = currentNote; // TODO: "where the script is currently executing"
	// this.originEntity = apiParams.originEntity; "Entity whose event triggered this execution" ??

	// TODO: apiParams

	// TODO: this.dayjs = dayjs;
	// TODO: this.xml2js = xml2js;

	// TODO: this.transactional = sql.transactional;
	// TODO: this.sql


	/**
	 * @param content string to escape
	 * @returns escaped string
	 */
	fun escapeHtml(content: String): String {
		// TODO: probably just do this in the API wrapper
		TODO("xxx")
	}

	/**
	 * @method
	 * @param {string} string to unescape
	 * @returns {string} unescaped string
	 */
	fun unescapeHtml(html: String): String {
		TODO("xxx") // also probably to do in the wrapper
	}

	/**
	 * @returns {{syncVersion, appVersion, buildRevision, dbVersion, dataDirectory, buildDate}|*} - object representing basic info about running Trilium version
	 */
	fun getAppInfo(): JSONObject {
		val o = JSONObject()
		o.put("syncVersion", Cache.Versions.SYNC_VERSION)
		o.put("appVersion", Cache.Versions.APP_VERSION)
		o.put("buildRevision", "HEAD")
		o.put("dbVersion", Cache.Versions.DATABASE_VERSION)
		o.put("dataDirectory", "/data/")
		o.put(
			"buildDate",
			DateTimeFormatter.ISO_INSTANT.format(
				OffsetDateTime.ofInstant(
					Instant.ofEpochMilli(BuildConfig.TIMESTAMP), ZoneId.of("UTC")
				)
			)
		)
		return o
	}

	/**
	 * @param {string} noteId
	 * @param {string} format - either 'html' or 'markdown'
	 * @param {string} zipFilePath
	 */
	fun exportSubtreeToZipFile(noteId: String, format: String, zipFilePath: String) {
		// TODO
	}

	// TODO __private.becca

	private fun encodeNote(note: Note?): JSONObject? {
		if (note == null) {
			return null
		}
		val obj = JSONObject()
		obj.put("id", note.id)
		obj.put("title", note.title())
		// TODO
		return obj
	}
}
