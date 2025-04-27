package eu.fliegendewurst.triliumdroid.activity.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
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
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import eu.fliegendewurst.triliumdroid.FrontendBackendApi
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.TreeItemAdapter
import eu.fliegendewurst.triliumdroid.controller.MainController
import eu.fliegendewurst.triliumdroid.data.Blob
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.Label
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.NoteId
import eu.fliegendewurst.triliumdroid.data.Relation
import eu.fliegendewurst.triliumdroid.database.Branches
import eu.fliegendewurst.triliumdroid.database.Notes
import eu.fliegendewurst.triliumdroid.database.Tree
import eu.fliegendewurst.triliumdroid.databinding.ActivityMainBinding
import eu.fliegendewurst.triliumdroid.dialog.ConfigureFabsDialog
import eu.fliegendewurst.triliumdroid.dialog.ConfigureFabsDialog.DELETE_NOTE
import eu.fliegendewurst.triliumdroid.dialog.ConfigureFabsDialog.EDIT_NOTE
import eu.fliegendewurst.triliumdroid.dialog.ConfigureFabsDialog.JUMP_TO_NOTE
import eu.fliegendewurst.triliumdroid.dialog.ConfigureFabsDialog.NOTE_MAP
import eu.fliegendewurst.triliumdroid.dialog.ConfigureFabsDialog.NOTE_NAVIGATION
import eu.fliegendewurst.triliumdroid.dialog.ConfigureFabsDialog.SHARE_NOTE
import eu.fliegendewurst.triliumdroid.dialog.ConfigureFabsDialog.SHOW_NOTE_TREE
import eu.fliegendewurst.triliumdroid.dialog.ConfigureFabsDialog.SYNC
import eu.fliegendewurst.triliumdroid.dialog.ModifyLabelsDialog
import eu.fliegendewurst.triliumdroid.dialog.ModifyRelationsDialog
import eu.fliegendewurst.triliumdroid.fragment.EmptyFragment
import eu.fliegendewurst.triliumdroid.fragment.EncryptedNoteFragment
import eu.fliegendewurst.triliumdroid.fragment.NavigationFragment
import eu.fliegendewurst.triliumdroid.fragment.NoteEditFragment
import eu.fliegendewurst.triliumdroid.fragment.NoteMapFragment
import eu.fliegendewurst.triliumdroid.fragment.NoteRelatedFragment
import eu.fliegendewurst.triliumdroid.fragment.NoteTreeFragment
import eu.fliegendewurst.triliumdroid.fragment.SyncErrorFragment
import eu.fliegendewurst.triliumdroid.fragment.note.CanvasNoteFragment
import eu.fliegendewurst.triliumdroid.fragment.note.NoteFragment
import eu.fliegendewurst.triliumdroid.service.Icon
import eu.fliegendewurst.triliumdroid.service.ProtectedSession
import eu.fliegendewurst.triliumdroid.util.ListAdapter
import eu.fliegendewurst.triliumdroid.util.Preferences
import eu.fliegendewurst.triliumdroid.view.ListViewAutoExpand
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


class MainActivity : AppCompatActivity() {
	private lateinit var binding: ActivityMainBinding
	lateinit var handler: Handler

	// menu items
	private var consoleLogMenuItem: MenuItem? = null
	private var executeScriptMenuItem: MenuItem? = null
	private var shareMenuItem: MenuItem? = null
	private var deleteMenuItem: MenuItem? = null
	private var consoleVisible: Boolean = false
	private var executeVisible: Boolean = false
	private var shareVisible: Boolean = false
	private var deleteVisible: Boolean = true

	private var hideLeftFAB = false
	private var hideRightFAB = false

	var tree: TreeItemAdapter? = null

	companion object {
		private const val TAG = "MainActivity"
		const val JUMP_TO_NOTE_ENTRY = "JUMP_TO_NOTE_ENTRY"
		private var controller: MainController = MainController()
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
		handler = Handler(applicationContext.mainLooper)

		controller.onCreate(this, intent)

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		val readOnly = Preferences.readOnlyMode()

		val toolbar = binding.toolbar
		toolbar.title = ""
		setSupportActionBar(toolbar)
		binding.toolbarIcon.setOnClickListener {
			controller.titleIconClicked(this)
		}
		binding.toolbarTitle.setOnClickListener {
			controller.titleClicked(this)
		}

		tree = TreeItemAdapter({
			controller.noteTreeClicked(this, it)
		}, {
			controller.noteTreeClickedLong(this, it)
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
							controller.newNote(this@MainActivity)
						}
						it.binding.buttonNewNoteSibling.setOnClickListener {
							controller.newNoteSibling(this@MainActivity)
						}
						for (buttonId in Preferences.prefs.all.keys.filter { pref ->
							pref.startsWith(
								"button"
							)
						}) {
							val view = LayoutInflater.from(this@MainActivity)
								.inflate(R.layout.button, it.binding.buttons, true)
							view.findViewById<ImageButton>(R.id.button_custom).setOnClickListener {
								val script = Preferences.prefs.getString(buttonId, "() => {}")
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
							ListAdapter(listOf(getString(R.string.action_note_map))) { type, convertView ->
								var vi = convertView
								if (vi == null) {
									vi = layoutInflater.inflate(
										R.layout.item_tree_note,
										it.binding.treeListSimple,
										false
									)
								}
								vi!!.findViewById<TextView>(R.id.note_icon).text =
									Icon.getUnicodeCharacter("bx bx-map-alt")
								val button = vi.findViewById<Button>(R.id.label)
								button.text = type
								button.setOnClickListener {
									if (type == getString(R.string.action_note_map)) {
										controller.globalNoteMap(this@MainActivity)
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
					tab.setText(R.string.sidebar_tab_1)
				}

				1 -> {
					tab.setText(R.string.sidebar_tab_2)
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

		val btnLabelsModify = binding.root.findViewById<Button>(R.id.button_labels_modify)
		btnLabelsModify.setOnClickListener {
			ModifyLabelsDialog.showDialog(this, getNoteLoaded() ?: return@setOnClickListener)
		}
		val btnRelationsEdit = binding.root.findViewById<Button>(R.id.button_relations_edit)
		btnRelationsEdit.setOnClickListener {
			ModifyRelationsDialog.showDialog(this, getNoteLoaded() ?: return@setOnClickListener)
		}
		val btnAddNotePath = binding.root.findViewById<Button>(R.id.button_note_paths_add)
		btnAddNotePath.setOnClickListener {
			controller.addNotePath(this)
		}
		if (readOnly) {
			btnLabelsModify.visibility = View.GONE
			btnRelationsEdit.visibility = View.GONE
			btnAddNotePath.visibility = View.GONE
		} else {
			btnLabelsModify.visibility = View.VISIBLE
			btnRelationsEdit.visibility = View.VISIBLE
			btnAddNotePath.visibility = View.VISIBLE
		}

		binding.fab.setOnClickListener {
			val action = ConfigureFabsDialog.getRightAction()
			performAction(action ?: return@setOnClickListener)
		}
		binding.fabTree.setOnClickListener {
			val action = ConfigureFabsDialog.getLeftAction()
			performAction(action ?: return@setOnClickListener)
		}
		// hide FABs until ready
		binding.fabTree.hide()
		binding.fab.hide()

		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				controller.handleOnBackPressed(this@MainActivity)
			}
		})
	}

	override fun onStart() {
		super.onStart()
		val rightAction = ConfigureFabsDialog.getRightAction()
		if (rightAction != null) {
			binding.fab.setImageResource(ConfigureFabsDialog.getIcon(rightAction))
		}
		hideRightFAB = rightAction == null
		val leftAction = ConfigureFabsDialog.getLeftAction()
		if (leftAction != null) {
			binding.fabTree.setImageResource(ConfigureFabsDialog.getIcon(leftAction))
		}
		hideLeftFAB = leftAction == null
		controller.onStart(this)
	}

	fun handleEmptyNote() {
		Log.d(TAG, "empty note, opening drawer")
		openDrawerTree()
	}

	/**
	 * @param action must be a key in [ConfigureFabsDialog.actions]
	 */
	fun performAction(action: String) {
		when (action) {
			SHOW_NOTE_TREE -> {
				controller.doMenuAction(this, R.id.action_show_note_tree)
			}

			JUMP_TO_NOTE -> {
				controller.doMenuAction(this, R.id.action_jump_to_note)
			}

			NOTE_NAVIGATION -> {
				controller.doMenuAction(this, R.id.action_note_navigation)
			}

			EDIT_NOTE -> {
				controller.doMenuAction(this, R.id.action_edit)
			}

			SHARE_NOTE -> {
				controller.doMenuAction(this, R.id.action_share)
			}

			DELETE_NOTE -> {
				controller.doMenuAction(this, R.id.action_delete)
			}

			NOTE_MAP -> {
				controller.doMenuAction(this, R.id.action_note_map)
			}

			SYNC -> {
				controller.doMenuAction(this, R.id.action_sync)
			}
		}
	}

	override fun onPause() {
		controller.onPause(this)
		super.onPause()
	}

	override fun onResume() {
		super.onResume()
		controller.onResume(this)
	}

	fun refreshTree() = lifecycleScope.launch {
		Log.w(TAG, "call to refreshTree ", Error())
		if (tree == null) {
			Log.w(TAG, "tried to refresh tree without tree")
			return@launch
		}
		val items = Tree.getTreeList(Branches.NONE_ROOT, 0)
		Log.d(TAG, "about to show ${items.size} tree items")
		tree!!.submitList(items)
	}

	fun treeIsEmpty() = tree?.currentList?.isEmpty() != false

	private var snackbar: Snackbar? = null

	fun indicateSyncStart() {
		snackbar?.dismiss()
		snackbar = Snackbar.make(
			binding.root,
			R.string.snackbar_sync_start, Snackbar.LENGTH_INDEFINITE
		)
		snackbar!!.setTextColor(resources.getColor(R.color.white, null))
		snackbar!!.view.layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
		snackbar!!.view.minimumWidth = 300
		(snackbar!!.view.layoutParams as FrameLayout.LayoutParams).gravity =
			Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
		snackbar!!.show()
	}

	fun indicateSyncProgress(remaining: Int) {
		snackbar?.setText(getString(R.string.snackbar_sync_progress, remaining))
	}

	fun indicateSyncError() {
		snackbar?.setText(R.string.snackbar_sync_error)
		snackbar?.duration = Snackbar.LENGTH_SHORT
		snackbar?.show()
	}

	fun indicateSyncDone(pulled: Int, pushed: Int) {
		snackbar?.setText(getString(R.string.snackbar_sync_done, pulled, pushed))
		snackbar?.duration = Snackbar.LENGTH_SHORT
		snackbar?.show()
	}

	override fun onCreateOptionsMenu(m: Menu?): Boolean {
		val menu = binding.toolbar.menu ?: return true
		menuInflater.inflate(R.menu.action_bar, menu)
		for (action in ConfigureFabsDialog.actions) {
			val menuItem = menu.findItem(action.value) ?: continue
			val pref = ConfigureFabsDialog.getPref(action.key) ?: continue
			menuItem.isVisible = pref.show
		}
		val frag = getFragment()
		val item = menu.findItem(R.id.action_edit)
		item.isVisible = !Preferences.readOnlyMode()
		if (frag is NoteEditFragment) {
			item?.setIcon(R.drawable.bx_save)
		} else {
			item?.setIcon(R.drawable.bx_edit_alt)
		}
		return true
	}

	override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
		if (menu == null) {
			return true
		}
		val enterProtectedSession = menu.findItem(R.id.action_enter_protected_session)
		val leaveProtectedSession = menu.findItem(R.id.action_leave_protected_session)
		enterProtectedSession?.isVisible = !ProtectedSession.isActive()
		leaveProtectedSession?.isVisible = ProtectedSession.isActive()
		consoleLogMenuItem = menu.findItem(R.id.action_console) ?: return true
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

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		Log.d(TAG, "menu action = ${item.itemId}")
		return controller.doMenuAction(this, item.itemId) || super.onOptionsItemSelected(item)
	}

	fun openDrawerTree() {
		if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
			binding.drawerLayout.closeDrawers()
		}
		binding.drawerLayout.openDrawer(GravityCompat.START)
	}

	fun fixVisibilityFABs(hideFabs: Boolean) {
		if (hideFabs) {
			binding.fabTree.hide()
			binding.fab.hide()
		} else {
			if (!hideLeftFAB) {
				binding.fabTree.show()
			}
			if (!hideRightFAB) {
				binding.fab.show()
			}
		}
	}

	fun showFragment(frag: Fragment, hideFabs: Boolean) {
		fixVisibilityFABs(hideFabs)
		val item = binding.toolbar.menu.findItem(R.id.action_edit)
		if (frag is NoteEditFragment) {
			item?.setIcon(R.drawable.bx_save)
		} else {
			item?.setIcon(R.drawable.bx_edit_alt)
		}
		if (supportFragmentManager.isStateSaved) {
			return // early return if activity is being shut down
		}
		supportFragmentManager.beginTransaction()
			.replace(R.id.fragment_container, frag)
			.commit()
	}

	@SuppressLint("SetJavaScriptEnabled")
	fun runScript(noteFrag: Fragment, script: String) {
		val webview = WebView(this)
		webview.settings.javaScriptEnabled = true
		webview.addJavascriptInterface(FrontendBackendApi(noteFrag, this, handler), "api")
		webview.webChromeClient = object : WebChromeClient() {
			override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
				Log.i(TAG, "console message ${consoleMessage?.message()}")
				enableConsoleLogAction()
				if (noteFrag is NoteFragment) {
					noteFrag.console.add(consoleMessage ?: return true)
				}
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


	private fun scrollTreeTo(noteId: NoteId) {
		val frag = supportFragmentManager.findFragmentByTag("f0") ?: return
		tree!!.select(noteId, true) {
			((frag as NoteTreeFragment).binding.treeList.layoutManager!! as LinearLayoutManager).scrollToPositionWithOffset(
				it,
				5
			)
		}
	}

	fun scrollTreeToBranch(branch: Branch) {
		val frag = supportFragmentManager.findFragmentByTag("f0") ?: return
		tree!!.scrollTo(branch) {
			((frag as NoteTreeFragment).binding.treeList.layoutManager!! as LinearLayoutManager).scrollToPositionWithOffset(
				it,
				5
			)
		}
	}

	/**
	 * @param notePath must end with "/${noteId}"
	 */
	fun navigateToPath(notePath: String) = lifecycleScope.launch {
		navigateTo(Notes.getNote(NoteId(notePath.split("/").last()))!!)
	}

	fun refreshTitle(note: Note?) {
		binding.toolbarTitle.text = note?.title() ?: "(nothing loaded)"
		binding.toolbarIcon.text =
			Icon.getUnicodeCharacter(note?.icon() ?: "bx bx-file-blank") ?: "\ueac6"
	}

	fun reloadNote(skipEditors: Boolean = false) {
		controller.reloadNote(this, skipEditors)
	}

	suspend fun refreshWidgets(noteToShow: Note) {
		var noteContent = noteToShow
		if (noteContent.invalid()) {
			noteContent = Notes.getNote(noteToShow.id) ?: return
		}
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
				button.text = attribute.target?.title() ?: "none"
				button.setOnClickListener {
					controller.relationTargetClicked(this, attribute)
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
					button.text = attribute.target?.title() ?: "none"
					button.setOnClickListener {
						controller.relationTargetClicked(this, attribute)
					}
					return@ListAdapter vi!!
				}
		}

		// note paths
		val notePaths = findViewById<ListView>(R.id.widget_note_paths_type_content)
		val paths = Branches.getNotePaths(noteContent.id)!!
		val currentPath = controller.currentPath()
		val pathsR = mutableListOf<Pair<String, Branch>>()
		for (x in paths) {
			pathsR.add(
				Pair(
					x.asReversed()
						.subList(1, x.size)
						.map { Notes.getNote(it.note)!!.title() }
						.joinToString(" > "),
					x.first()
				)
			)
		}
		val arrayAdapter =
			ListAdapter(pathsR) { path: Pair<String, Branch>, convertView: View? ->
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
						controller.notePathEdit(this, pathBranch, noteContent)
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
				controller.notePathClicked(this, pathSelected, noteContent)
			}
		}

		// revisions
		val revisionListView = findViewById<ListViewAutoExpand>(R.id.widget_note_revisions_list)
		val revs = noteContent.revisions().toMutableList()
		val revAdapter = ListAdapter(revs) { revision, convertView ->
			var vi = convertView
			if (vi == null) {
				vi = layoutInflater.inflate(
					R.layout.item_note_revision,
					notePaths,
					false
				)
			}
			val timestamp = vi!!.findViewById<TextView>(R.id.revision_time)
			val view = vi!!.findViewById<Button>(R.id.revision_view)
			val restore = vi!!.findViewById<Button>(R.id.revision_restore)
			val delete = vi!!.findViewById<Button>(R.id.revision_delete)
			timestamp.text = revision.dateCreated.substring(0 until 19)
			view.setOnClickListener {
				controller.revisionView(this, revision)
			}
			restore.setOnClickListener {
				controller.revisionRestore(this, revision)
			}
			delete.setOnClickListener {
				controller.revisionDelete(this, revision) {
					revs.remove(revision)
					(revisionListView.adapter as ListAdapter<*>).notifyDataSetChanged()
				}
			}
			return@ListAdapter vi!!
		}
		revisionListView.adapter = revAdapter

		// metadata
		val noteId = findViewById<TextView>(R.id.widget_note_info_id_content)
		noteId.text = noteContent.id.rawId()
		val noteType = findViewById<TextView>(R.id.widget_note_info_type_content)
		noteType.text = noteContent.type
		val encrypted = findViewById<CheckBox>(R.id.widget_basic_properties_encrypt_content)
		encrypted.isChecked = noteContent.isProtected
		encrypted.isEnabled = ProtectedSession.isActive()
		encrypted.setOnCheckedChangeListener { _, isChecked ->
			lifecycleScope.launch {
				Notes.changeNoteProtection(noteContent.id, isChecked)
			}
		}
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
		controller.navigateTo(this, note, branch)
	}

	fun load(note: Note, branch: Branch?, content: Blob? = null) {
		Log.i(TAG, "loading note ${note.id} @ branch ${branch?.id} @ revision $content")
		Preferences.setLastNote(note.id)
		// make sure note is visible
		lifecycleScope.launch {
			val path = Branches.getNotePath(note.id)
			val expandedAny = controller.ensurePathIsExpanded(path)
			if (expandedAny) {
				refreshTree()
			}
			tree!!.select(note.id)
			val noteContent = Notes.getNoteWithContent(note.id)
			if (noteContent == null) {
				if (note.id == Notes.ROOT) {
					Log.e(TAG, "fatal error, missing content for root note")
					return@launch
				}
				Log.w(TAG, "attempted to load deleted note, loading root instead")
				load(Notes.getRootNote(), null)
				return@launch
			}
			if (noteContent.isProtected && !ProtectedSession.isActive()) {
				showFragment(EncryptedNoteFragment(), true)
			} else if (noteContent.type == "canvas") {
				getCanvasNoteFragment().load(noteContent, content)
			} else {
				getNoteFragment().load(noteContent, content)
			}
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
	}

	override fun onDestroy() {
		super.onDestroy()
		controller.onDestroy()
	}

	fun hasFragment() = supportFragmentManager.findFragmentById(R.id.fragment_container) != null

	fun getFragment(): Fragment {
		val hostFragment =
			supportFragmentManager.findFragmentById(R.id.fragment_container)
		return when (hostFragment) {
			is NoteFragment, is CanvasNoteFragment, is NoteEditFragment, is EmptyFragment, is NoteMapFragment, is NavigationFragment, is SyncErrorFragment, is EncryptedNoteFragment -> {
				hostFragment
			}

			is NavHostFragment -> {
				val frags = hostFragment.childFragmentManager.fragments
				frags[0]
			}

			else -> {
				return hostFragment!!
			}
		}
	}

	fun getCanvasNoteFragment(): CanvasNoteFragment {
		var frag = getFragment()
		if (frag is CanvasNoteFragment) {
			return frag
		}
		// replace fragment
		frag = CanvasNoteFragment()
		showFragment(frag, false)
		return frag
	}

	fun getNoteFragment(): NoteFragment {
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
			return runBlocking { Notes.getNoteWithContent(id) }
		}
		return null
	}
}
