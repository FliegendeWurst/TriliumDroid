package eu.fliegendewurst.triliumdroid

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.StrictMode
import android.system.ErrnoException
import android.system.OsConstants
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.text.parseAsHtml
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes


class MainActivity : AppCompatActivity() {
	private lateinit var binding: ActivityMainBinding
	internal lateinit var handler: Handler
	private lateinit var prefs: SharedPreferences
	private lateinit var consoleLogMenuItem: MenuItem
	private lateinit var executeScriptMenuItem: MenuItem
	private lateinit var shareMenuItem: MenuItem
	private var consoleVisible: Boolean = false
	private var executeVisible: Boolean = false
	private var shareVisible: Boolean = false
	private var firstNote: String? = null

	companion object {
		private const val TAG = "MainActivity"
		const val JUMP_TO_NOTE_ENTRY = "JUMP_TO_NOTE_ENTRY"
		private const val LAST_NOTE = "LastNote"
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

		oneTimeSetup()

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		handler = Handler(applicationContext.mainLooper)

		val toolbar = binding.toolbar
		setSupportActionBar(toolbar)

		ArrayAdapter.createFromResource(
			this,
			R.array.note_types_array,
			android.R.layout.simple_spinner_item
		).also { adapter ->
			adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
			findViewById<Spinner>(R.id.widget_basic_properties_type_content).adapter = adapter
		}

		Cache.initializeDatabase(applicationContext)

		prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
		val adapter = TreeItemAdapter({
			navigateTo(Cache.getNote(it.note)!!)
		}, {
			Cache.toggleBranch(it)
			refreshTree()
		})
		binding.treeList.adapter = adapter
		tree = adapter

		// add custom buttons
		for (buttonId in prefs.all.keys.filter { it.startsWith("button") }) {
			val view = LayoutInflater.from(this).inflate(R.layout.button, binding.buttons, true)
			val script = prefs.getString(buttonId, "() => {}")
			view.findViewById<ImageButton>(R.id.button_custom).setOnClickListener {
				Log.i(TAG, "executing button script $script")
				runScript(getNoteFragment(), "($script)()")
			}
		}

		binding.fab.setOnClickListener {
			JumpToNoteDialog.showDialog(this)
		}

		if (prefs.getString("hostname", null) == null) {
			Log.i(TAG, "starting setup!")
			val intent = Intent(this, SetupActivity::class.java)
			startActivity(intent)
		} else {
			ConnectionUtil.setup(prefs, {
				handler.post {
					startSync(handler)
				}
			}, {
				handler.post {
					handleError(it)
					showInitialNote(true)
				}
			})
		}
	}

	private fun refreshTree() {
		val items = Cache.getTreeList("none_root", 0)
		Log.i(TAG, "about to show ${items.size} tree items")
		tree!!.submitList(items)
	}

	private fun handleError(it: Exception) {
		var toastText: String? = null
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
		Toast.makeText(
			this, toastText,
			Toast.LENGTH_LONG
		).show()
	}

	private fun startSync(handler: Handler, resetView: Boolean = true) {
		val contextView = binding.fragmentContainer

		val snackbar = Snackbar.make(contextView, "Sync: starting...", Snackbar.LENGTH_INDEFINITE)
		snackbar.view.layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
		snackbar.view.minimumWidth = 300
		(snackbar.view.layoutParams as FrameLayout.LayoutParams).gravity =
			Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
		snackbar.show()
		lifecycleScope.launch(Dispatchers.IO) {
			Cache.syncStart({
				handler.post {
					snackbar.setText("Sync: $it outstanding...")
				}
			}, {
				handler.post {
					handleError(it)
				}
			}, {
				handler.post {
					snackbar.setText("Sync: ${it.first} pulled, ${it.second} pushed")
					snackbar.duration = Snackbar.LENGTH_SHORT
					snackbar.show()
				}
				Cache.getTreeData()
				handler.post {
					showInitialNote(resetView)
				}
			})
		}
	}

	private fun showInitialNote(resetView: Boolean) {
		refreshTree()
		if (resetView) {
			val n = firstNote ?: prefs.getString(LAST_NOTE, "root")!!
			navigateTo(Cache.getNote(n) ?: return)
			// first use: open the drawer
			if (!prefs.contains(LAST_NOTE)) {
				binding.drawerLayout.openDrawer(GravityCompat.START)
			}
		} else {
			navigateTo(Cache.getNote(getNoteFragment().getNoteId()) ?: return)
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
		consoleLogMenuItem.isVisible = consoleVisible
		executeScriptMenuItem.isVisible = executeVisible
		shareMenuItem.isVisible = shareVisible
		return true
	}

	fun setupActions(consoleLog: Boolean, execute: Boolean, share: Boolean) {
		consoleVisible = consoleLog
		executeVisible = execute
		shareVisible = share
		consoleLogMenuItem.isVisible = consoleVisible
		executeScriptMenuItem.isVisible = executeVisible
		shareMenuItem.isVisible = shareVisible
		if (!consoleLog || !execute || !share) {
			invalidateOptionsMenu()
		}
	}

	fun enableConsoleLogAction() {
		consoleLogMenuItem.isVisible = true
		consoleVisible = true
	}

	@SuppressLint("SetJavaScriptEnabled")
	override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
		R.id.action_edit -> {
			val id = getNoteFragment().getNoteId()
			supportFragmentManager.beginTransaction()
				.replace(R.id.fragment_container, NoteEditFragment(id))
				.addToBackStack(null)
				.commit()
			true
		}

		R.id.action_share -> {
			val note = Cache.getNoteWithContent(getNoteFragment().getNoteId())!!
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
						"eu.fliegendewurst.fileprovider",
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
			val script = String(Cache.getNoteWithContent(noteId)!!.content!!)
			runScript(noteFrag, script)
			true
		}

		R.id.action_sync -> {
			startSync(handler, false)
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
			startActivity(Intent(this, SetupActivity::class.java))
			true
		}

		else -> {
			// If we got here, the user's action was not recognized.
			// Invoke the superclass to handle it.
			super.onOptionsItemSelected(item)
		}
	}

	@SuppressLint("SetJavaScriptEnabled")
	private fun runScript(noteFrag: NoteFragment, script: String) {
		val webview = WebView(this)
		webview.settings.javaScriptEnabled = true
		webview.addJavascriptInterface(FrontendBackendApi(noteFrag, this), "api")
		webview.webChromeClient = object : WebChromeClient() {
			override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
				Log.i(TAG, "console message ${consoleMessage?.message()}")
				enableConsoleLogAction()
				noteFrag.console.add(consoleMessage ?: return true)
				return true
			}
		}
		Log.i(TAG, "executing $script")
		webview.evaluateJavascript(script) {
			Log.i(TAG, "done executing code note!")
			webview.destroy()
		}
		webview.addJavascriptInterface(FrontendBackendApi(noteFrag, this), "api")
	}


	private fun scrollTreeTo(noteId: String) {
		tree!!.select(noteId)
		val pos = Cache.getBranchPosition(noteId) ?: return
		(binding.treeList.layoutManager!! as LinearLayoutManager).scrollToPositionWithOffset(pos, 5)
	}

	fun navigateToPath(notePath: String) {
		navigateTo(Cache.getNote(notePath.split("/").last())!!)
	}

	fun navigateTo(note: Note) {
		Log.i(TAG, "loading note ${note.id}")
		prefs.edit().putString(LAST_NOTE, note.id).apply()
		// make sure note is visible
		val path = Cache.getNotePath(note.id)
		var expandedAny = false
		for (n in path) {
			// expanded = the children of this note are visible
			if (!n.expanded) {
				Cache.toggleBranch(n)
				expandedAny = true
			}
		}
		if (expandedAny) {
			refreshTree()
		}
		tree!!.select(note.id)
		getNoteFragment().load(note.id)
		binding.drawerLayout.closeDrawers()
		supportActionBar?.title = note.title
		scrollTreeTo(note.id)
	}

	override fun onDestroy() {
		super.onDestroy()
		Cache.closeDatabase()
	}

	private fun getNoteFragment(): NoteFragment {
		val hostFragment =
			supportFragmentManager.findFragmentById(R.id.fragment_container)
		return if (hostFragment is NoteFragment) {
			hostFragment
		} else {
			val frags = (hostFragment as NavHostFragment).childFragmentManager.fragments
			frags[0] as NoteFragment
		}
	}

	fun getNoteLoaded(): Note {
		return Cache.getNoteWithContent(getNoteFragment().getNoteId())!!
	}
}
