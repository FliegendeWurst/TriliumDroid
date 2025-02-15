package eu.fliegendewurst.triliumdroid.activity.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.StrictMode
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.text.parseAsHtml
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import eu.fliegendewurst.triliumdroid.activity.AboutActivity
import eu.fliegendewurst.triliumdroid.AlarmReceiver
import eu.fliegendewurst.triliumdroid.BuildConfig
import eu.fliegendewurst.triliumdroid.Cache
import eu.fliegendewurst.triliumdroid.ConnectionUtil
import eu.fliegendewurst.triliumdroid.FrontendBackendApi
import eu.fliegendewurst.triliumdroid.fragment.NoteEditFragment
import eu.fliegendewurst.triliumdroid.fragment.NoteFragment
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.activity.SetupActivity
import eu.fliegendewurst.triliumdroid.TreeItemAdapter
import eu.fliegendewurst.triliumdroid.activity.WelcomeActivity
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.Label
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.Relation
import eu.fliegendewurst.triliumdroid.databinding.ActivityMainBinding
import eu.fliegendewurst.triliumdroid.dialog.AskForNameDialog
import eu.fliegendewurst.triliumdroid.dialog.ConfigureFabsDialog
import eu.fliegendewurst.triliumdroid.dialog.CreateNewNoteDialog
import eu.fliegendewurst.triliumdroid.dialog.JumpToNoteDialog
import eu.fliegendewurst.triliumdroid.dialog.ModifyLabelsDialog
import eu.fliegendewurst.triliumdroid.dialog.ModifyRelationsDialog
import eu.fliegendewurst.triliumdroid.dialog.SelectNoteDialog
import eu.fliegendewurst.triliumdroid.fragment.EmptyFragment
import eu.fliegendewurst.triliumdroid.fragment.NoteMapFragment
import eu.fliegendewurst.triliumdroid.fragment.NoteRelatedFragment
import eu.fliegendewurst.triliumdroid.fragment.NoteTreeFragment
import eu.fliegendewurst.triliumdroid.service.DateNotes
import eu.fliegendewurst.triliumdroid.service.Icon
import eu.fliegendewurst.triliumdroid.util.CrashReport
import eu.fliegendewurst.triliumdroid.util.ListAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import javax.net.ssl.SSLProtocolException
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes


class MainActivity : AppCompatActivity() {
	private lateinit var binding: ActivityMainBinding
	lateinit var handler: Handler
	private lateinit var prefs: SharedPreferences
	private var resourceOverlay: Resources? = null

	// menu items
	private var consoleLogMenuItem: MenuItem? = null
	private var executeScriptMenuItem: MenuItem? = null
	private var shareMenuItem: MenuItem? = null
	private var deleteMenuItem: MenuItem? = null
	private var consoleVisible: Boolean = false
	private var executeVisible: Boolean = false
	private var shareVisible: Boolean = false
	private var deleteVisible: Boolean = true

	// initial note to show
	private var firstNote: String? = null

	// navigation history
	private val noteHistory: MutableList<HistoryItem> = mutableListOf()

	companion object {
		private const val TAG = "MainActivity"
		const val JUMP_TO_NOTE_ENTRY = "JUMP_TO_NOTE_ENTRY"
		private const val LAST_NOTE = "LastNote"
		private const val LAST_REPORT = "LastReport"
		var tree: TreeItemAdapter? = null
	}

	private fun oneTimeSetup() {
		StrictMode.setVmPolicy(
			StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
				.detectLeakedClosableObjects()
				.penaltyLog()
				.build()
		)

		// Create the NotificationChannel.
		val name = getString(R.string.channel_name)
		val importance = NotificationManager.IMPORTANCE_DEFAULT
		val mChannel = NotificationChannel(AlarmReceiver.CHANNEL_ID, name, importance)
		// Register the channel with the system. You can't change the importance
		// or other notification behaviors after this.
		val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.createNotificationChannel(mChannel)

		val oldHandler = Thread.getDefaultUncaughtExceptionHandler()

		Thread.setDefaultUncaughtExceptionHandler { paramThread, paramThrowable ->
			CrashReport.saveReport(this, paramThread, paramThrowable)
			oldHandler?.uncaughtException(
				paramThread,
				paramThrowable
			)
		}

		val lastReported = prefs.getString(LAST_REPORT, "2020") ?: "2020"
		val pendingReports = CrashReport.pendingReports(this).filter { x -> x.name > lastReported }
		val toReport = pendingReports.maxByOrNull { x -> x.name }
		if (toReport != null) {
			AlertDialog.Builder(this)
				.setTitle(getString(R.string.dialog_report_app_crash))
				.setMessage(
					getString(
						R.string.label_report_crash,
						toReport.name
					)
				)
				.setPositiveButton(
					android.R.string.ok
				) { _, _ ->
					prefs.edit().putString(LAST_REPORT, toReport.name)
						.apply()
					// read report and create email
					val intent = Intent(Intent.ACTION_SENDTO).apply {
						data = Uri.parse("mailto:")
						putExtra(
							Intent.EXTRA_EMAIL,
							arrayOf("arne.keller+triliumdroid-crash@posteo.de")
						)
						putExtra(
							Intent.EXTRA_SUBJECT,
							"TriliumDroid crash, version ${BuildConfig.VERSION_NAME}"
						)
						putExtra(Intent.EXTRA_TEXT, toReport.readText())
					}
					startActivity(Intent.createChooser(intent, "Choose email client:"))

					try {
						pendingReports.forEach { x -> x.deleteOnExit() }
					} catch (e: IOException) {
						Log.e(TAG, "failed to delete crash report ", e)
					}
				}
				.setNegativeButton(android.R.string.cancel, null)
				.setOnDismissListener {
					try {
						pendingReports.forEach { x -> x.delete() }
					} catch (e: IOException) {
						Log.e(TAG, "failed to delete crash report ", e)
					}
				}
				.setIconAttribute(android.R.attr.alertDialogIcon)
				.show()
		}
	}

	fun checkNotificationPermission(): Boolean {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			return ActivityCompat.checkSelfPermission(
				this,
				Manifest.permission.POST_NOTIFICATIONS
			) == PackageManager.PERMISSION_GRANTED
		}
		return true
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		firstNote = intent.extras?.getString("note")
		prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

		if (intent?.action == Intent.ACTION_SEND) {
			if (intent.type == "text/plain" || intent.type == "text/html") {
				val text = intent.getStringExtra(Intent.EXTRA_TEXT)
				if (text != null) {
					if (Cache.haveDatabase(this)) {
						Cache.initializeDatabase(applicationContext)
						val inbox = DateNotes.getInboxNote()
						val note = Cache.createChildNote(inbox, text.lineSequence().first())
						Cache.setNoteContent(note.id, text)
					}
				}
			}
			finish()
		}

		oneTimeSetup()

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		handler = Handler(applicationContext.mainLooper)

		val toolbar = binding.toolbar
		toolbar.title = ""
		setSupportActionBar(toolbar)
		binding.toolbarTitle.setOnClickListener {
			val note = getNoteLoaded() ?: return@setOnClickListener
			AskForNameDialog.showDialog(this, R.string.dialog_rename_note, note.title) {
				Cache.renameNote(note, it)
				refreshTree()
				refreshTitle(note)
			}
		}

		tree = TreeItemAdapter({
			navigateTo(Cache.getNote(it.note)!!, it)
		}, {
			Cache.toggleBranch(it)
			refreshTree()
		})

		binding.pager.adapter = object : FragmentStateAdapter(this) {
			override fun getItemCount(): Int {
				return 2
			}

			override fun createFragment(position: Int): Fragment {
				if (position == 0) {
					val frag = NoteTreeFragment()
					frag.initLater {
						it.binding.treeList.adapter = tree
						it.binding.buttonNewNote.setOnClickListener {
							val note = getNoteLoaded()
							if (note == null) {
								Toast.makeText(
									this@MainActivity, getString(R.string.toast_no_note),
									Toast.LENGTH_LONG
								).show()
								return@setOnClickListener
							}
							CreateNewNoteDialog.showDialog(this@MainActivity, true, note)
						}
						it.binding.buttonNewNoteSibling.setOnClickListener {
							val note = getNoteLoaded()
							if (note == null) {
								Toast.makeText(
									this@MainActivity, getString(R.string.toast_no_note),
									Toast.LENGTH_LONG
								).show()
								return@setOnClickListener
							}
							CreateNewNoteDialog.showDialog(
								this@MainActivity,
								false,
								note
							)
						}
						for (buttonId in prefs.all.keys.filter { pref -> pref.startsWith("button") }) {
							val view = LayoutInflater.from(this@MainActivity)
								.inflate(R.layout.button, it.binding.buttons, true)
							view.findViewById<ImageButton>(R.id.button_custom).setOnClickListener {
								val script = prefs.getString(buttonId, "() => {}")
								Log.i(TAG, "executing button script $script")
								runScript(getNoteFragment(), "($script)()")
							}
						}
					}
					return frag
				} else if (position == 1) {
					val frag = NoteTreeFragment()
					frag.initLater {
						it.binding.treeList.visibility = View.GONE
						it.binding.treeListSimple.visibility = View.VISIBLE
						it.binding.treeListSimple.adapter =
							ListAdapter(listOf("Note Map")) { type, convertView ->
								var vi = convertView
								if (vi == null) {
									vi = layoutInflater.inflate(
										R.layout.item_tree_note,
										it.binding.treeListSimple,
										false
									)
								}
								val button = vi!!.findViewById<Button>(R.id.label)
								button.text = type
								button.setOnClickListener {
									if (type == "Note Map") {
										noteHistory.add(NoteMapItem(null))
										noteHistory.last().restore(this@MainActivity)
									}
								}
								return@ListAdapter vi
							}
						it.binding.buttons.visibility = View.GONE
					}
					return frag
				}
				throw IllegalArgumentException("wrong position $position")
			}
		}
		val tabLayout = binding.tabLayout
		TabLayoutMediator(tabLayout, binding.pager) { tab, position ->
			when (position) {
				0 -> {
					tab.text = "NOTES"
				}

				1 -> {
					tab.text = "SPECIAL"
				}
			}
		}.attach()


		ArrayAdapter.createFromResource(
			this,
			R.array.note_types_array,
			android.R.layout.simple_spinner_item
		).also { adapter ->
			adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
			findViewById<Spinner>(R.id.widget_basic_properties_type_content).adapter = adapter
		}

		binding.root.findViewById<Button>(R.id.button_labels_modify).setOnClickListener {
			ModifyLabelsDialog.showDialog(this, getNoteLoaded() ?: return@setOnClickListener)
		}
		binding.root.findViewById<Button>(R.id.button_relations_edit).setOnClickListener {
			ModifyRelationsDialog.showDialog(this, getNoteLoaded() ?: return@setOnClickListener)
		}
		binding.root.findViewById<Button>(R.id.button_note_paths_add).setOnClickListener {
			val loaded = getNoteLoaded() ?: return@setOnClickListener
			JumpToNoteDialog.showDialogReturningNote(this, R.string.dialog_select_note) {
				Cache.cloneNote(it, loaded)
			}
		}

		binding.fab.setOnClickListener {
			val action = ConfigureFabsDialog.getRightAction(prefs)
			performAction(action ?: return@setOnClickListener)
		}
		binding.fabTree.setOnClickListener {
			val action = ConfigureFabsDialog.getLeftAction(prefs)
			performAction(action ?: return@setOnClickListener)
		}

		if (!Cache.haveDatabase(this)) {
			val intent = Intent(this, WelcomeActivity::class.java)
			startActivity(intent)
		}

		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				if (noteHistory.isNotEmpty()) {
					noteHistory.removeAt(noteHistory.size - 1)
				}
				while (true) {
					if (noteHistory.isEmpty()) {
						finish()
						break
					} else {
						val entry = noteHistory[noteHistory.size - 1]
						if (entry.restore(this@MainActivity)) {
							return
						} else {
							noteHistory.removeAt(noteHistory.size - 1)
						}
					}
				}
			}
		})

		if (Cache.haveDatabase(this)) {
			Cache.initializeDatabase(applicationContext)
		}
	}

	override fun onStart() {
		super.onStart()
		if (Cache.haveDatabase(this)) {
			Cache.initializeDatabase(applicationContext)
			if (prefs.getString("hostname", null) == null) {
				Log.i(TAG, "starting setup!")
				val intent = Intent(this, SetupActivity::class.java)
				startActivity(intent)
			} else if (Cache.lastSync == null && noteHistory.isEmpty()) {
				ConnectionUtil.setup(prefs, {
					handler.post {
						startSync(handler)
					}
				}, {
					Cache.getTreeData("")
					refreshTree()
					handler.post {
						handleError(it)
					}
				})
				Cache.getTreeData("")
				showInitialNote(true)
			} else if (noteHistory.isEmpty()) {
				showInitialNote(true)
			}
		}
		binding.fab.setImageResource(
			ConfigureFabsDialog.getIcon(
				ConfigureFabsDialog.getRightAction(
					prefs
				)
			)
		)
		binding.fabTree.setImageResource(
			ConfigureFabsDialog.getIcon(
				ConfigureFabsDialog.getLeftAction(
					prefs
				)
			)
		)
	}

	fun handleEmptyNote() {
		Log.d(TAG, "empty note, opening drawer")
		binding.drawerLayout.openDrawer(GravityCompat.START)
	}

	private fun performAction(action: String) {
		when (action) {
			"showNoteTree" -> {
				binding.drawerLayout.openDrawer(GravityCompat.START)
			}

			"jumpToNote" -> {
				JumpToNoteDialog.showDialog(this)
			}

			"editNote" -> {
				doMenuAction(R.id.action_edit)
			}

			"shareNote" -> {
				doMenuAction(R.id.action_share)
			}

			"deleteNote" -> {
				doMenuAction(R.id.action_delete)
			}

			"noteMap" -> {
				doMenuAction(R.id.action_note_map)
			}

			"sync" -> {
				doMenuAction(R.id.action_sync)
			}
		}
	}

	private var loadedNoteId: String? = null

	override fun onPause() {
		// TODO: maybe save the edited content somewhere
		when (val fragment = getFragment()) {
			is NoteFragment -> {
				loadedNoteId = fragment.getNoteId()
				supportFragmentManager.beginTransaction()
					.replace(R.id.fragment_container, EmptyFragment())
					.commit()
			}
		}
		super.onPause()
	}

	override fun onResume() {
		super.onResume()
		if (loadedNoteId == null) {
			return
		}
		if (!Cache.haveDatabase(this)) {
			loadedNoteId = null
			tree?.submitList(emptyList())
			refreshTitle(null)
			supportFragmentManager.beginTransaction()
				.replace(R.id.fragment_container, EmptyFragment())
				.commit()
			return
		}
		when (getFragment()) {
			is EmptyFragment -> {
				val frag = NoteFragment()
				frag.loadLater(loadedNoteId!!)
				supportFragmentManager.beginTransaction()
					.replace(R.id.fragment_container, frag)
					.commit()
			}
		}
	}

	fun refreshTree() {
		val items = Cache.getTreeList("none_root", 0)
		Log.i(TAG, "about to show ${items.size} tree items")
		tree!!.submitList(items)
	}

	private fun handleError(it: Exception) {
		var toastText: String? = null
		if (it is SSLProtocolException) {
			// TODO ask the user if they need mTLS
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
			this, toastText,
			Toast.LENGTH_LONG
		).show()
	}

	private fun startSync(handler: Handler, resetView: Boolean = true) {
		val snackbar = Snackbar.make(binding.root, "Sync: starting...", Snackbar.LENGTH_INDEFINITE)
		snackbar.view.layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
		snackbar.view.minimumWidth = 300
		(snackbar.view.layoutParams as FrameLayout.LayoutParams).gravity =
			Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
		snackbar.show()
		lifecycleScope.launch(Dispatchers.IO) {
			Cache.initializeDatabase(applicationContext)
			ConnectionUtil.setup(prefs, {
				Cache.syncStart({
					handler.post {
						snackbar.setText("Sync: $it outstanding...")
					}
				}, {
					handler.post {
						handleError(it)
					}
					// ensure the snackbar doesn't stay visible
					handler.post {
						snackbar.setText("Sync: error!")
						snackbar.duration = Snackbar.LENGTH_SHORT
						snackbar.show()
					}
				}, {
					handler.post {
						snackbar.setText("Sync: ${it.first} pulled, ${it.second} pushed")
						snackbar.duration = Snackbar.LENGTH_SHORT
						snackbar.show()
					}
					Cache.getTreeData("")
					handler.post {
						showInitialNote(resetView)
					}
				})
			}, {
				Cache.getTreeData("")
				handler.post {
					handleError(it)
					showInitialNote(true)
					snackbar.setText("Sync: error!")
					snackbar.duration = Snackbar.LENGTH_SHORT
					snackbar.show()
				}
			})
		}
	}

	private fun showInitialNote(resetView: Boolean) {
		refreshTree()
		if (resetView) {
			var n = firstNote ?: prefs.getString(LAST_NOTE, "root")!!
			if (Cache.getNote(n) == null) {
				n = "root" // may happen in case of new database or note deleted
			}
			val note = Cache.getNote(n) ?: return
			navigateTo(note, note.branches.first())
			// first use: open the drawer
			if (!prefs.contains(LAST_NOTE)) {
				binding.drawerLayout.openDrawer(GravityCompat.START)
			}
		} else {
			navigateTo(Cache.getNote(getNoteFragment().getNoteId() ?: return) ?: return)
		}
	}

	override fun onCreateOptionsMenu(m: Menu?): Boolean {
		val menu = binding.toolbar.menu
		menuInflater.inflate(R.menu.action_bar, menu)
		return true
	}

	override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
		consoleLogMenuItem = menu?.findItem(R.id.action_console) ?: return true
		executeScriptMenuItem = menu.findItem(R.id.action_execute) ?: return true
		shareMenuItem = menu.findItem(R.id.action_share) ?: return true
		deleteMenuItem = menu.findItem(R.id.action_delete) ?: return true
		consoleLogMenuItem?.isVisible = consoleVisible
		executeScriptMenuItem?.isVisible = executeVisible
		shareMenuItem?.isVisible = shareVisible
		deleteMenuItem?.isVisible = deleteVisible
		return true
	}

	fun setupActions(consoleLog: Boolean, execute: Boolean, share: Boolean, isRoot: Boolean) {
		consoleVisible = consoleLog
		executeVisible = execute
		shareVisible = share
		deleteVisible = !isRoot
		consoleLogMenuItem?.isVisible = consoleVisible
		executeScriptMenuItem?.isVisible = executeVisible
		shareMenuItem?.isVisible = shareVisible
		deleteMenuItem?.isVisible = deleteVisible
		if (!consoleLog || !execute || !share || isRoot) {
			invalidateOptionsMenu()
		}
	}

	fun enableConsoleLogAction() {
		consoleLogMenuItem?.isVisible = true
		consoleVisible = true
	}

	@SuppressLint("SetJavaScriptEnabled")
	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		return doMenuAction(item.itemId) || super.onOptionsItemSelected(item)
	}

	private fun doMenuAction(actionId: Int): Boolean {
		return when (actionId) {
			R.id.action_edit -> {
				when (val fragment = getFragment()) {
					is NoteFragment -> {
						val id = fragment.getNoteId() ?: return true
						noteHistory.add(NoteEditItem(Cache.getNote(id)!!))
						noteHistory.last().restore(this)
					}

					is NoteEditFragment -> {
						noteHistory.removeAt(noteHistory.size - 1)
						noteHistory.last().restore(this)
					}

					else -> {
						Log.e(TAG, "failed to identify fragment " + fragment.javaClass)
					}
				}
				true
			}

			R.id.action_share -> {
				val id = getNoteFragment().getNoteId() ?: return true
				val note = Cache.getNoteWithContent(id)!!
				val sendIntent: Intent = Intent().apply {
					action = Intent.ACTION_SEND
					if (note.type == "text" || note.type == "code") {
						if (note.mime == "text/html") {
							val html = String(note.content!!)
							putExtra(Intent.EXTRA_HTML_TEXT, html)
							putExtra(Intent.EXTRA_TEXT, html.parseAsHtml())
						} else {
							putExtra(Intent.EXTRA_TEXT, String(note.content!!))
						}
					} else if (note.type == "image") {
						var f = Path(
							cacheDir.absolutePath,
							"images"
						)
						f = f.createDirectories().resolve("${note.id}.${note.mime.split('/')[1]}")
						f.writeBytes(note.content!!)
						val contentUri = FileProvider.getUriForFile(
							this@MainActivity,
							applicationContext.packageName + ".provider",
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

				val shareIntent = Intent.createChooser(sendIntent, note.title)
				startActivity(shareIntent)
				true
			}

			R.id.action_execute -> {
				Log.i(TAG, "executing code note")
				val noteFrag = getNoteFragment()
				val noteId = getNoteFragment().getNoteId()
				if (noteId != null) {
					val script = String(Cache.getNoteWithContent(noteId)!!.content!!)
					runScript(noteFrag, script)
				}
				true
			}

			R.id.action_delete -> {
				val note = getNoteLoaded()
				if (note == null || note.id == "root") {
					Toast.makeText(
						this, getString(R.string.toast_cannot_delete_root),
						Toast.LENGTH_SHORT
					).show()
				} else {
					val path = getNotePathLoaded() ?: Cache.getNotePath(note.id)[0]
					AlertDialog.Builder(this)
						.setTitle("Delete note")
						.setMessage("Do you really want to delete this note?")
						.setPositiveButton(
							android.R.string.ok
						) { _, _ ->
							if (!Cache.deleteNote(path)) {
								Toast.makeText(
									this, getString(R.string.toast_could_not_delete),
									Toast.LENGTH_SHORT
								).show()
							}
							navigateTo(Cache.getNote(path.parentNote)!!)
							refreshTree()
						}
						.setNegativeButton(android.R.string.cancel, null).show()
				}
				true
			}

			R.id.action_sync -> {
				startSync(handler, false)
				true
			}

			R.id.action_note_map -> {
				val frag = getFragment()
				if (frag is NoteMapFragment) {
					noteHistory.removeAt(noteHistory.size - 1)
					noteHistory.last().restore(this)
				} else if (frag is NoteFragment) {
					val id = frag.getNoteId()
					if (id != null) {
						noteHistory.add(NoteMapItem(Cache.getNote(id)!!))
						noteHistory.last().restore(this)
					}
				}
				true
			}

			R.id.action_console -> {
				val dialog = AlertDialog.Builder(this)
					.setTitle(R.string.action_console_log)
					.setView(R.layout.dialog_console)
					.create()
				val text = StringBuilder()
				for (entry in getNoteFragment().console) {
					text.append(entry.message()).append("\n")
				}
				dialog.show()
				dialog.findViewById<TextView>(R.id.dialog_console_output)!!.text = text
				true
			}

			R.id.action_settings -> {
				startActivity(Intent(this, SetupActivity::class.java))
				true
			}

			R.id.action_about -> {
				startActivity(Intent(this, AboutActivity::class.java))
				true
			}

			else -> {
				// If we got here, the user's action was not recognized.
				// Invoke the superclass to handle it.
				false
			}
		}
	}

	fun showFragment(frag: Fragment, hideFabs: Boolean) {
		if (hideFabs) {
			binding.fabTree.hide()
			binding.fab.hide()
		} else {
			binding.fabTree.show()
			binding.fab.show()
		}
		supportFragmentManager.beginTransaction()
			.replace(R.id.fragment_container, frag)
			.commit()
	}

	@SuppressLint("SetJavaScriptEnabled")
	fun runScript(noteFrag: NoteFragment, script: String) {
		val webview = WebView(this)
		webview.settings.javaScriptEnabled = true
		webview.addJavascriptInterface(FrontendBackendApi(noteFrag, this, handler), "api")
		webview.webChromeClient = object : WebChromeClient() {
			override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
				Log.i(TAG, "console message ${consoleMessage?.message()}")
				enableConsoleLogAction()
				noteFrag.console.add(consoleMessage ?: return true)
				return true
			}
		}
		Log.i(TAG, "executing $script")
		val modifiedScript = """(() => {
			api.getDayNote = (day, root) => {
				return JSON.parse(api.getDayNoteInternal(day, root));			
			};
			$script
		})()""".trimIndent()
		webview.evaluateJavascript(modifiedScript) {
			Log.i(TAG, "done executing code note!")
			webview.destroy()
		}
		webview.addJavascriptInterface(FrontendBackendApi(noteFrag, this, handler), "api")
	}


	private fun scrollTreeTo(noteId: String) {
		tree!!.select(noteId)
		val pos = Cache.getBranchPosition(noteId) ?: return
		val frag = supportFragmentManager.findFragmentByTag("f0") ?: return
		((frag as NoteTreeFragment).binding.treeList.layoutManager!! as LinearLayoutManager).scrollToPositionWithOffset(
			pos,
			5
		)
	}

	private fun scrollTreeToBranch(branch: Branch) {
		val pos = branch.cachedTreeIndex ?: return
		val frag = supportFragmentManager.findFragmentByTag("f0") ?: return
		((frag as NoteTreeFragment).binding.treeList.layoutManager!! as LinearLayoutManager).scrollToPositionWithOffset(
			pos,
			5
		)
	}

	fun navigateToPath(notePath: String) {
		navigateTo(Cache.getNote(notePath.split("/").last())!!)
	}

	private fun refreshTitle(note: Note?) {
		Log.d(TAG, "refreshing title")
		binding.toolbarTitle.text = note?.title ?: "(nothing loaded)"
		binding.toolbarIcon.text =
			Icon.getUnicodeCharacter(note?.getLabel("iconClass") ?: "bx bx-file-blank")
	}

	fun refreshWidgets(noteContent: Note) {
		// attributes
		val attributes = noteContent.getLabels()
		val ownedAttributes = attributes.filter { x -> !x.inherited && !x.templated }
		val ownedAttributesList = findViewById<ListView>(R.id.widget_owned_attributes_type_content)
		ownedAttributesList.adapter =
			ListAdapter(
				ownedAttributes
			) { attribute: Label, convertView: View? ->
				var vi = convertView
				if (vi == null) {
					vi = layoutInflater.inflate(
						R.layout.item_attribute,
						ownedAttributesList,
						false
					)
				}
				vi!!.findViewById<TextView>(R.id.label_attribute_name).text = attribute.name
				vi.findViewById<TextView>(R.id.label_attribute_value).text = attribute.value()
				return@ListAdapter vi
			}
		val inheritedAttributes = attributes.filter { x -> x.inherited || x.templated }
		val inheritedAttributesList =
			findViewById<ListView>(R.id.widget_inherited_attributes_type_content)
		if (inheritedAttributes.isEmpty()) {
			findViewById<View>(R.id.widget_inherited_attributes).visibility = View.GONE
		} else {
			findViewById<View>(R.id.widget_inherited_attributes).visibility = View.VISIBLE
			inheritedAttributesList.adapter =
				ListAdapter(
					inheritedAttributes
				) { attribute: Label, convertView: View? ->
					var vi = convertView
					if (vi == null) {
						vi = layoutInflater.inflate(
							R.layout.item_attribute,
							inheritedAttributesList,
							false
						)
					}
					vi!!.findViewById<TextView>(R.id.label_attribute_name).text = attribute.name
					vi!!.findViewById<TextView>(R.id.label_attribute_value).text = attribute.value()
					return@ListAdapter vi!!
				}
		}

		// relations
		val relations = noteContent.getRelations()
		val ownedRelations = relations.filter { x -> !x.inherited && !x.templated }
		val ownedRelationsList = findViewById<ListView>(R.id.widget_owned_relations_type_content)
		ownedRelationsList.adapter =
			ListAdapter(
				ownedRelations
			) { attribute: Relation, convertView: View? ->
				var vi = convertView
				if (vi == null) {
					vi = layoutInflater.inflate(
						R.layout.item_relation,
						ownedRelationsList,
						false
					)
				}
				vi!!.findViewById<TextView>(R.id.label_relation_name).text = attribute.name
				val button = vi!!.findViewById<Button>(R.id.button_relation_target)
				button.text = attribute.target?.title ?: "none"
				button.setOnClickListener {
					navigateTo(attribute.target ?: return@setOnClickListener)
				}
				return@ListAdapter vi!!
			}
		val inheritedRelations = relations.filter { x -> x.inherited || x.templated }
		val inheritedRelationsList =
			findViewById<ListView>(R.id.widget_inherited_relations_type_content)
		if (inheritedRelations.isEmpty()) {
			findViewById<View>(R.id.widget_inherited_relations).visibility = View.GONE
		} else {
			findViewById<View>(R.id.widget_inherited_relations).visibility = View.VISIBLE
			inheritedRelationsList.adapter =
				ListAdapter(
					inheritedRelations
				) { attribute: Relation, convertView: View? ->
					var vi = convertView
					if (vi == null) {
						vi = layoutInflater.inflate(
							R.layout.item_relation,
							inheritedRelationsList,
							false
						)
					}
					vi!!.findViewById<TextView>(R.id.label_relation_name).text = attribute.name
					val button = vi!!.findViewById<Button>(R.id.button_relation_target)
					button.text = attribute.target?.title ?: "none"
					button.setOnClickListener {
						navigateTo(attribute.target ?: return@setOnClickListener)
					}
					return@ListAdapter vi!!
				}
		}

		// note paths
		val notePaths = findViewById<ListView>(R.id.widget_note_paths_type_content)
		val paths = Cache.getNotePaths(noteContent.id)!!
		val currentPath = noteHistory.last().branch()
		val branchToString = { x: List<Branch> ->
			Pair(
				x.asReversed()
					.subList(1, x.size)
					.joinToString(" > ") { Cache.getNote(it.note)!!.title }, x.first()
			)
		}
		val arrayAdapter =
			ListAdapter(paths.map(branchToString)) { path: Pair<String, Branch>, convertView: View? ->
				val pathString = path.first
				val pathBranch = path.second
				var vi = convertView
				if (vi == null) {
					vi = layoutInflater.inflate(
						R.layout.item_note_path,
						notePaths,
						false
					)
				}
				val textView = vi!!.findViewById<TextView>(R.id.label_note_path)
				val button = vi!!.findViewById<Button>(R.id.button_note_path_edit)
				textView.text = pathString
				if (path.second == currentPath) {
					textView.setTypeface(null, Typeface.BOLD)
					button.visibility = View.VISIBLE
					button.setOnClickListener {
						JumpToNoteDialog.showDialogReturningNote(
							this,
							R.string.dialog_select_parent_note
						) { newParent ->
							val newParentNote =
								Cache.getNote(newParent.note) ?: return@showDialogReturningNote
							val children = (listOf(newParent) + newParentNote.computeChildren()
								.toList()).toMutableList()
							children.remove(pathBranch)
							if (children.size == 1) {
								Cache.moveBranch(pathBranch, newParent, 0)
							} else {
								SelectNoteDialog.showDialogReturningNote(
									this,
									children
								) { siblingNote ->
									val siblingIndex = children.indexOf(siblingNote)
									if (siblingIndex == 0) {
										// insert as first element
										Cache.moveBranch(
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
													Cache.moveBranch(
														children[i],
														newParent,
														(i - 1) * 10
													)
												} else {
													Cache.moveBranch(children[i], newParent, i * 10)
												}
											}
											Cache.moveBranch(pathBranch, newParent, ourIndex * 10)
										} else {
											val ourPosition =
												siblingNote.position + positionDelta / 2
											Cache.moveBranch(pathBranch, newParent, ourPosition)
										}
									} else {
										Cache.moveBranch(
											pathBranch,
											newParent,
											siblingNote.position + 10
										)
									}
									refreshTree()
									refreshWidgets(noteContent)
								}
							}
						}
					}
				} else {
					textView.setTypeface(null, Typeface.NORMAL)
					button.visibility = View.GONE
				}
				return@ListAdapter vi!!
			}
		notePaths.adapter = arrayAdapter
		notePaths.onItemClickListener = OnItemClickListener { _, _, position, _ ->
			// switch to the note path in the tree
			if (position >= 0 && position < paths.size) {
				val pathSelected = paths[position]
				if (ensurePathIsExpanded(pathSelected)) {
					refreshTree()
				}
				if (pathSelected.first().cachedTreeIndex != null) {
					noteHistory.last().setBranch(pathSelected.first())
					refreshWidgets(noteContent)
					scrollTreeToBranch(pathSelected.first())
					binding.drawerLayout.closeDrawers()
					binding.drawerLayout.openDrawer(GravityCompat.START)
				}
			}
		}

		val noteId = findViewById<TextView>(R.id.widget_note_info_id_content)
		noteId.text = noteContent.id
		val noteType = findViewById<TextView>(R.id.widget_note_info_type_content)
		noteType.text = noteContent.type
		val noteCreated = findViewById<TextView>(R.id.widget_note_info_created_content)
		noteCreated.text = noteContent.created.substring(0, 19)
		val noteModified = findViewById<TextView>(R.id.widget_note_info_modified_content)
		noteModified.text = noteContent.modified.substring(0, 19)

		val frag = getFragment()
		if (frag is NoteFragment) {
			frag.refreshHeader(noteContent)
		}
	}

	fun navigateTo(note: Note, branch: Branch? = null) {
		noteHistory.add(NoteItem(note, branch))
		load(note, branch)
	}

	fun load(note: Note, branch: Branch?) {
		Log.i(TAG, "loading note ${note.id}")
		prefs.edit().putString(LAST_NOTE, note.id).apply()
		// make sure note is visible
		val path = Cache.getNotePath(note.id)
		val expandedAny = ensurePathIsExpanded(path)
		if (expandedAny) {
			refreshTree()
		}
		tree!!.select(note.id)
		getNoteFragment().load(note.id)
		val noteContent = Cache.getNoteWithContent(note.id)!!
		binding.drawerLayout.closeDrawers()
		refreshTitle(noteContent)
		if (branch != null) {
			scrollTreeToBranch(branch)
		} else {
			scrollTreeTo(noteContent.id)
		}

		// update right drawer
		refreshWidgets(noteContent)
	}

	private fun ensurePathIsExpanded(path: List<Branch>): Boolean {
		var expandedAny = false
		for (n in path) {
			// expanded = the children of this note are visible
			if (!n.expanded) {
				Cache.toggleBranch(n)
				expandedAny = true
			}
		}
		return expandedAny
	}

	override fun onDestroy() {
		super.onDestroy()
		Cache.closeDatabase()
	}

	private fun getFragment(): Fragment {
		val hostFragment =
			supportFragmentManager.findFragmentById(R.id.fragment_container)
		return if (hostFragment is NoteFragment || hostFragment is NoteEditFragment || hostFragment is EmptyFragment || hostFragment is NoteMapFragment) {
			hostFragment
		} else {
			val frags = (hostFragment as NavHostFragment).childFragmentManager.fragments
			frags[0]
		}
	}

	private fun getNoteFragment(): NoteFragment {
		var frag = getFragment()
		if (frag is NoteFragment) {
			return frag
		}
		// replace fragment
		frag = NoteFragment()
		showFragment(frag, false)
		return frag
	}

	fun getNoteLoaded(): Note? {
		val frag = getFragment()
		if (frag is NoteRelatedFragment) {
			val id = frag.getNoteId() ?: return null
			return Cache.getNoteWithContent(id)
		}
		return null
	}

	private fun getNotePathLoaded(): Branch? {
		return noteHistory.last().branch()
	}
}
