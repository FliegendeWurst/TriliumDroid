package eu.fliegendewurst.triliumdroid.controller

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.text.parseAsHtml
import androidx.lifecycle.lifecycleScope
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.activity.AboutActivity
import eu.fliegendewurst.triliumdroid.activity.SetupActivity
import eu.fliegendewurst.triliumdroid.activity.WelcomeActivity
import eu.fliegendewurst.triliumdroid.activity.main.BlobItem
import eu.fliegendewurst.triliumdroid.activity.main.HistoryItem
import eu.fliegendewurst.triliumdroid.activity.main.HistoryList
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity.Companion.tree
import eu.fliegendewurst.triliumdroid.activity.main.NavigationItem
import eu.fliegendewurst.triliumdroid.activity.main.NoteEditItem
import eu.fliegendewurst.triliumdroid.activity.main.NoteItem
import eu.fliegendewurst.triliumdroid.activity.main.NoteMapItem
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.NoteRevision
import eu.fliegendewurst.triliumdroid.database.Branches
import eu.fliegendewurst.triliumdroid.database.Cache
import eu.fliegendewurst.triliumdroid.database.NoteRevisions
import eu.fliegendewurst.triliumdroid.database.Notes
import eu.fliegendewurst.triliumdroid.dialog.AskForNameDialog
import eu.fliegendewurst.triliumdroid.dialog.ConfigureWidgetDialog
import eu.fliegendewurst.triliumdroid.dialog.CreateNewNoteDialog
import eu.fliegendewurst.triliumdroid.dialog.JumpToNoteDialog
import eu.fliegendewurst.triliumdroid.dialog.SelectNoteDialog
import eu.fliegendewurst.triliumdroid.dialog.YesNoDialog
import eu.fliegendewurst.triliumdroid.fragment.EmptyFragment
import eu.fliegendewurst.triliumdroid.fragment.NoteEditFragment
import eu.fliegendewurst.triliumdroid.fragment.NoteFragment
import eu.fliegendewurst.triliumdroid.fragment.NoteMapFragment
import eu.fliegendewurst.triliumdroid.fragment.SyncErrorFragment
import eu.fliegendewurst.triliumdroid.service.DateNotes
import eu.fliegendewurst.triliumdroid.service.ProtectedSession
import eu.fliegendewurst.triliumdroid.sync.ConnectionUtil
import eu.fliegendewurst.triliumdroid.sync.Sync
import eu.fliegendewurst.triliumdroid.util.Preferences
import eu.fliegendewurst.triliumdroid.widget.NoteWidget
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.security.KeyStore
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

/**
 * Controller for the main activity.
 * Receives user input events and notifies UI about changes.
 */
class MainController {
	private companion object {
		private const val TAG = "MainController"
	}

	/**
	 * Navigation stack.
	 */
	private val noteHistory = HistoryList()
	private var firstAction: HistoryItem? = null

	/**
	 * Whether to show the next sync error as a full [SyncErrorFragment]
	 */
	private var showSyncError: Boolean = false

	/**
	 * True iff the activity is currently running, i.e. after onResume but before onPause.
	 */
	private var active: Boolean = false

	/**
	 * When unloading the note on screen leave, the note ID that was loaded.
	 */
	private var loadedNoteId: String? = null

	/**
	 * Called when the app is started.
	 *
	 * Possible outcomes:
	 * - database present
	 * 	 1. sync not configured → SetupActivity
	 * 	 2. not synced yet → start sync, show initial note
	 * 	 3. already synced → show initial note
	 * - database not present → WelcomeActivity
	 */
	fun onStart(activity: MainActivity) {
		if (firstAction != null) {
			if (Cache.haveDatabase(activity)) {
				runBlocking { Cache.initializeDatabase(activity.applicationContext) }
			}
			return
		}
		if (Cache.haveDatabase(activity)) {
			if (Preferences.hostname() == null) {
				Log.i(TAG, "starting setup!")
				val intent = Intent(activity, SetupActivity::class.java)
				activity.startActivity(intent)
			} else if (Cache.lastSync == null && noteHistory.isEmpty()) {
				activity.lifecycleScope.launch {
					ConnectionUtil.setup(activity, {
						activity.handler.post {
							startSync(activity)
						}
					}, {
						activity.lifecycleScope.launch {
							Cache.getTreeData("")
							activity.refreshTree()
							handleError(activity, it)
						}
					})
					Cache.getTreeData("")
					activity.refreshTree()
					activity.showInitialNote(true)
				}
			} else if (noteHistory.isEmpty()) {
				Log.d(TAG, "last sync is ${Cache.lastSync}, showing initial note")
				activity.showInitialNote(true)
			}
		} else {
			Log.d(TAG, "no database, starting welcome activity")
			val intent = Intent(activity, WelcomeActivity::class.java)
			activity.startActivity(intent)
		}
	}

	fun onResume(activity: MainActivity) {
		active = true
		if (firstAction != null) {
			noteHistory.addAndRestore(firstAction!!, activity)
			firstAction = null
			activity.lifecycleScope.launch {
				Cache.getTreeData("")
				activity.refreshTree()
			}
			return
		}
		// if the user deleted the database, nuke the history too
		if (!Preferences.hasSyncContext() || !Cache.haveDatabase(activity)) {
			noteHistory.reset()
			loadedNoteId = null
			tree?.submitList(emptyList())
			activity.refreshTitle(null)
			activity.showFragment(EmptyFragment(), true)
			return
		}
		if (loadedNoteId == null) {
			return
		}
		when (activity.getFragment()) {
			is EmptyFragment -> {
				activity.lifecycleScope.launch {
					val frag = NoteFragment()
					frag.loadLater(Notes.getNoteWithContent(loadedNoteId!!))
					activity.showFragment(frag, false)
				}
			}
		}
		loadedNoteId = null
	}

	fun handleOnBackPressed(activity: MainActivity) {
		if (noteHistory.goBack(activity)) {
			activity.finish()
		}
	}

	fun onPause(activity: MainActivity) {
		// TODO: maybe save the edited content somewhere
		when (val fragment = activity.getFragment()) {
			is NoteFragment -> {
				loadedNoteId = fragment.getNoteId()
				activity.showFragment(EmptyFragment(), true)
			}
		}
		active = false
	}

	fun doMenuAction(activity: MainActivity, actionId: Int): Boolean = when (actionId) {
		R.id.action_enter_protected_session -> {
			val error = ProtectedSession.enter()
			if (error != null) {
				Toast.makeText(activity, error, Toast.LENGTH_SHORT).show()
			} else {
				reloadNote(activity)
			}
			true
		}

		R.id.action_leave_protected_session -> {
			ProtectedSession.leave()
			reloadNote(activity)
			true
		}

		R.id.action_show_note_tree -> {
			activity.openDrawerTree()
			true
		}

		R.id.action_jump_to_note -> {
			JumpToNoteDialog.showDialog(activity)
			true
		}

		R.id.action_note_navigation -> {
			activity.lifecycleScope.launch {
				var note = Notes.getNote(noteHistory.noteId()) ?: return@launch
				var branch = noteHistory.branch() ?: note.branches[0]
				if (note.computeChildren().isEmpty()) {
					note = Notes.getNote(branch.parentNote)!!
					branch = note.branches[0] // TODO
				}
				noteHistory.addAndRestore(NavigationItem(note, branch), activity)
			}
			true
		}

		R.id.action_edit -> run {
			when (val fragment = activity.getFragment()) {
				is NoteFragment -> {
					val id = fragment.getNoteId() ?: return true
					noteHistory.addAndRestore(
						NoteEditItem(runBlocking { Notes.getNote(id)!! }),
						activity
					)
				}

				is NoteEditFragment -> {
					if (noteHistory.goBack(activity)) {
						activity.finish()
					}
				}

				else -> {
					Log.e(TAG, "failed to identify fragment " + fragment.javaClass)
				}
			}
			true
		}

		R.id.action_share -> run {
			val id = activity.getNoteFragment().getNoteId() ?: return true
			val note = runBlocking { Notes.getNoteWithContent(id)!! }
			val content = note.content() ?: return true
			val sendIntent: Intent = Intent().apply {
				action = Intent.ACTION_SEND
				if (note.type == "text" || note.type == "code") {
					if (note.mime == "text/html") {
						val html = String(content)
						putExtra(Intent.EXTRA_HTML_TEXT, html)
						putExtra(Intent.EXTRA_TEXT, html.parseAsHtml())
					} else {
						putExtra(Intent.EXTRA_TEXT, String(content))
					}
				} else if (note.type == "image") {
					var f = Path(
						activity.cacheDir.absolutePath,
						"images"
					)
					f = f.createDirectories().resolve("${note.id}.${note.mime.split('/')[1]}")
					f.writeBytes(content)
					val contentUri = FileProvider.getUriForFile(
						activity,
						activity.applicationContext.packageName + ".provider",
						f.toFile()
					)
					clipData = ClipData.newRawUri("", contentUri)
					putExtra(
						Intent.EXTRA_STREAM,
						contentUri
					)
					addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
				} else {
					putExtra(Intent.EXTRA_TEXT, "(cannot share this note type)")
				}
				type = note.mime
			}

			val shareIntent = Intent.createChooser(sendIntent, note.title())
			activity.startActivity(shareIntent)
			true
		}

		R.id.action_execute -> run {
			Log.i(TAG, "executing code note")
			val noteFrag = activity.getNoteFragment()
			val noteId = noteFrag.getNoteId()
			if (noteId != null) {
				val note = runBlocking { Notes.getNoteWithContent(noteId)!! }
				val script = String(note.content() ?: return true)
				activity.runScript(noteFrag, script)
			}
			true
		}

		R.id.action_delete -> {
			val note = activity.getNoteLoaded()
			if (note == null || note.id == "root") {
				Toast.makeText(
					activity, activity.getString(R.string.toast_cannot_delete_root),
					Toast.LENGTH_SHORT
				).show()
			} else {
				val path = noteHistory.branch() ?: Branches.getNotePath(note.id)[0]
				AlertDialog.Builder(activity)
					.setTitle("Delete note")
					.setMessage("Do you really want to delete this note?")
					.setPositiveButton(
						android.R.string.ok
					) { _, _ ->
						if (runBlocking { !Notes.deleteNote(path) }) {
							Toast.makeText(
								activity, activity.getString(R.string.toast_could_not_delete),
								Toast.LENGTH_SHORT
							).show()
						}
						navigateTo(activity, runBlocking { Notes.getNote(path.parentNote)!! })
						activity.refreshTree()
					}
					.setNegativeButton(android.R.string.cancel, null).show()
			}
			true
		}

		R.id.action_sync -> {
			showSyncError = true
			startSync(activity, true)
			true
		}

		R.id.action_note_map -> {
			val frag = activity.getFragment()
			if (frag is NoteMapFragment) {
				noteHistory.goBack(activity)
			} else if (frag is NoteFragment) {
				val id = frag.getNoteId()
				if (id != null) {
					noteHistory.addAndRestore(
						NoteMapItem(runBlocking { Notes.getNote(id)!! }),
						activity
					)
				}
			}
			true
		}

		R.id.action_console -> {
			val dialog = AlertDialog.Builder(activity)
				.setTitle(R.string.action_console_log)
				.setView(R.layout.dialog_console)
				.create()
			val text = StringBuilder()
			for (entry in activity.getNoteFragment().console) {
				text.append(entry.message()).append("\n")
			}
			dialog.show()
			dialog.findViewById<TextView>(R.id.dialog_console_output)!!.text = text
			true
		}

		R.id.action_settings -> {
			activity.startActivity(Intent(activity, SetupActivity::class.java))
			true
		}

		R.id.action_about -> {
			activity.startActivity(Intent(activity, AboutActivity::class.java))
			true
		}

		else -> {
			// If we got here, the user's action was not recognized.
			// Invoke the superclass to handle it.
			false
		}
	}

	fun navigateTo(activity: MainActivity, note: Note, branch: Branch? = null) {
		noteHistory.addAndRestore(NoteItem(note, branch), activity)
	}

	fun revisionView(activity: MainActivity, revision: NoteRevision) {
		activity.lifecycleScope.launch {
			val content = revision.content()
			noteHistory.addAndRestore(BlobItem(revision.note, content), activity)
		}
	}

	fun revisionRestore(activity: MainActivity, revision: NoteRevision) {
		activity.lifecycleScope.launch {
			val contentToRestore = revision.content()
			revision.note.updateContent(contentToRestore.content, true)
			reloadNote(activity)
		}
	}

	fun revisionDelete(activity: MainActivity, revision: NoteRevision, uiCallback: () -> Unit) {
		YesNoDialog.show(
			activity,
			R.string.title_delete_revision,
			R.string.text_delete_revision
		) {
			activity.lifecycleScope.launch {
				NoteRevisions.delete(revision)
				uiCallback.invoke()
			}
		}
	}

	/**
	 * Widget with given ID was used to launch the app.
	 */
	fun widgetUsed(activity: MainActivity, appWidgetId: Int) {
		val action = Preferences.widgetAction(appWidgetId)
		if (action == null) {
			// user must configure this widget before use
			ConfigureWidgetDialog.showDialog(activity, appWidgetId) {
				val intent = Intent(activity, NoteWidget::class.java)
				intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
				val ids = intArrayOf(appWidgetId)
				intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
				activity.sendBroadcast(intent)
			}
		} else {
			firstAction = action
		}
	}

	/**
	 * App received text from another app (share target).
	 */
	suspend fun textShared(context: Context, text: String) {
		if (Cache.haveDatabase(context)) {
			Cache.initializeDatabase(context.applicationContext)
			val inbox = DateNotes.getInboxNote()
			val note = Notes.createChildNote(
				inbox,
				text.lineSequence().first()
			)
			Notes.setNoteContent(note.id, text)
		}
	}

	/**
	 * User clicked on the note title in the action bar.
	 */
	fun titleClicked(activity: MainActivity) {
		val note = activity.getNoteLoaded() ?: return
		if (note.isProtected && !ProtectedSession.isActive()) {
			return
		}
		AskForNameDialog.showDialog(activity, R.string.dialog_rename_note, note.title()) {
			activity.lifecycleScope.launch {
				Notes.renameNote(note, it)
				activity.refreshTree()
				activity.refreshTitle(note)
			}
		}
	}

	/**
	 * User clicked on new (child) note button.
	 */
	fun newNote(activity: MainActivity) {
		val note = activity.getNoteLoaded()
		if (note == null) {
			Toast.makeText(
				activity, activity.getString(R.string.toast_no_note),
				Toast.LENGTH_LONG
			).show()
			return
		}
		CreateNewNoteDialog.showDialog(activity, true, note)
	}

	/**
	 * User clicked on new (sibling) note button.
	 */
	fun newNoteSibling(activity: MainActivity) {
		val note = activity.getNoteLoaded()
		if (note == null) {
			Toast.makeText(activity, activity.getString(R.string.toast_no_note), Toast.LENGTH_LONG)
				.show()
			return
		}
		CreateNewNoteDialog.showDialog(activity, false, note)
	}

	fun globalNoteMap(activity: MainActivity) {
		noteHistory.addAndRestore(NoteMapItem(null), activity)
	}

	fun noteTreeClicked(activity: MainActivity, branch: Branch) {
		activity.lifecycleScope.launch {
			navigateTo(activity, Notes.getNoteWithContent(branch.note)!!, branch)
		}
	}

	fun noteTreeClickedLong(activity: MainActivity, branch: Branch) {
		runBlocking {
			Branches.toggleBranch(branch)
		}
		activity.refreshTree()
	}

	fun addNotePath(activity: MainActivity) {
		val loaded = activity.getNoteLoaded() ?: return
		JumpToNoteDialog.showDialogReturningNote(activity, R.string.dialog_select_note) {
			runBlocking {
				Branches.cloneNote(it, loaded)
			}
		}
	}

	fun notePathEdit(activity: MainActivity, pathBranch: Branch, note: Note) {
		JumpToNoteDialog.showDialogReturningNote(
			activity,
			R.string.dialog_select_parent_note
		) { newParent ->
			activity.lifecycleScope.launch {
				val newParentNote =
					Notes.getNote(newParent.note)
						?: return@launch
				val children = (listOf(newParent) + newParentNote.computeChildren()
					.toList()).toMutableList()
				children.remove(pathBranch)
				if (children.size == 1) {
					Branches.moveBranch(pathBranch, newParent, 0)
				} else {
					SelectNoteDialog.showDialogReturningNote(
						activity,
						children
					) { siblingNote ->
						activity.lifecycleScope.launch {
							val siblingIndex = children.indexOf(siblingNote)
							if (siblingIndex == 0) {
								// insert as first element
								Branches.moveBranch(
									pathBranch,
									newParent,
									children[1].position - 10
								)
							} else if (siblingIndex < children.size - 1) {
								val insertBefore = children[siblingIndex + 1]
								val positionDelta =
									insertBefore.position - siblingNote.position
								if (positionDelta <= 1) {
									// re-order entire list
									val ourIndex = siblingIndex + 1
									for (i in 1 until children.size) {
										if (i < ourIndex) {
											Branches.moveBranch(
												children[i],
												newParent,
												(i - 1) * 10
											)
										} else {
											Branches.moveBranch(
												children[i],
												newParent,
												i * 10
											)

										}
									}
									Branches.moveBranch(
										pathBranch,
										newParent,
										ourIndex * 10
									)
								} else {
									val ourPosition =
										siblingNote.position + positionDelta / 2
									Branches.moveBranch(
										pathBranch,
										newParent,
										ourPosition
									)

								}
							} else {
								Branches.moveBranch(
									pathBranch,
									newParent,
									siblingNote.position + 10
								)
							}
							activity.refreshTree()
							activity.refreshWidgets(note)
						}
					}
				}
			}
		}
	}

	fun reloadNote(activity: MainActivity, skipEditors: Boolean = false) {
		if (!active) {
			return
		}
		if (skipEditors && noteHistory.last() == NoteEditItem::class) {
			return
		}
		noteHistory.restore(activity)
	}

	fun switchedBranch(branch: Branch) = noteHistory.setBranch(branch)

	fun currentPath() = noteHistory.branch()

	private fun handleError(activity: MainActivity, it: Exception) {
		var toastText: String? = null
		val cause = it.cause
		val cause2 = cause?.cause
		if (it is SSLHandshakeException && cause is CertificateException && cause2 is CertPathValidatorException) {
			val cert = cause2.certPath.certificates[0]
			AlertDialog.Builder(activity)
				.setTitle(R.string.title_trust_sync_server_certificate)
				.setMessage(cert.toString())
				.setPositiveButton(
					android.R.string.ok
				) { dialog, _ ->
					val ks: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
						load(null)
					}
					ks.setEntry("syncServer", KeyStore.TrustedCertificateEntry(cert), null)
					activity.lifecycleScope.launch {
						ConnectionUtil.resetClient(activity) {
							startSync(activity)
						}
						dialog.dismiss()
					}
				}
				.setNegativeButton(android.R.string.cancel, null).show()
		}

		if (it is SSLException && it.message == "Unable to parse TLS packet header") {
			toastText = "Invalid TLS configuration"
		}
		if (it is IllegalStateException) {
			toastText = it.message ?: "IllegalStateException"
		}
		if (it.cause?.cause is ErrnoException) {
			when ((it.cause!!.cause as ErrnoException).errno) {
				OsConstants.ECONNREFUSED -> {
					toastText = "Sync server refused connection"
				}

				OsConstants.EHOSTUNREACH -> {
					toastText = "Sync server unreachable"
				}
			}
		}
		if (toastText == null) {
			toastText = it.toString()
		}
		Log.e(TAG, "error ", it)
		Toast.makeText(
			activity, toastText,
			Toast.LENGTH_LONG
		).show()
		if (activity.getFragment() is EmptyFragment || activity.getFragment() is SyncErrorFragment || showSyncError) {
			val frag = SyncErrorFragment()
			frag.showError(it)
			activity.showFragment(frag, true)
		}
		showSyncError = false
	}

	private fun startSync(activity: MainActivity, resetView: Boolean = true) {
		activity.indicateSyncStart()
		activity.lifecycleScope.launch {
			Cache.initializeDatabase(activity.applicationContext)
			ConnectionUtil.setup(activity, {
				activity.lifecycleScope.launch {
					Sync.syncStart({
						activity.handler.post {
							activity.indicateSyncProgress(it)
						}
					}, {
						activity.handler.post {
							handleError(activity, it)
						}
						// ensure the snackbar doesn't stay visible
						activity.handler.post {
							activity.indicateSyncError()
						}
					}, {
						activity.lifecycleScope.launch {
							activity.indicateSyncDone(it.first, it.second)
							Cache.getTreeData("")
							activity.showInitialNote(resetView)
							showSyncError = false
						}
					})
				}
			}, {
				activity.lifecycleScope.launch {
					Cache.getTreeData("")
					val showInitial = !showSyncError
					handleError(activity, it)
					if (showInitial) {
						activity.showInitialNote(true)
					}
					activity.indicateSyncError()
				}
			})
		}
	}

	fun onDestroy() {
		Cache.closeDatabase()
		// TODO: check if the activity is about to be re-created
		noteHistory.reset()
		firstAction = null
		showSyncError = false
		active = false
		loadedNoteId = null
	}
}
