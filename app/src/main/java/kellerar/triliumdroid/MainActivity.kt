package kellerar.triliumdroid

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import kellerar.triliumdroid.data.Note
import kellerar.triliumdroid.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Exception


class MainActivity : AppCompatActivity() {
	private lateinit var binding: ActivityMainBinding
	private lateinit var handler: Handler
	private var jumpRequestId = 0

	companion object {
		private const val TAG = "MainActivity"
		public const val JUMP_TO_NOTE_ENTRY = "JUMP_TO_NOTE_ENTRY"
		var tree: TreeItemAdapter? = null
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		handler = Handler(applicationContext.mainLooper)

		val toolbar = findViewById<Toolbar>(R.id.toolbar)
		setSupportActionBar(toolbar)

		Cache.initializeDatabase(applicationContext)

		val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
		val adapter = TreeItemAdapter({
			navigateTo(Cache.getNote(it.note)!!)
		}, {
			Cache.toggleBranch(it)
			refreshTree()
		})
		binding.treeList.adapter = adapter
		tree = adapter

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
				}
			})

			try {
				binding.drawerLayout.openDrawer(GravityCompat.START)
			} catch (t: Throwable) {
				Log.e(TAG, "fatality!", t)
			}
		}
	}

	private fun refreshTree() {
		val items = Cache.getTreeList("root", 0)
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

	private fun startSync(handler: Handler) {
		val contextView = findViewById<View>(R.id.fragment_container)

		val snackbar = Snackbar.make(contextView, "Sync: starting...", Snackbar.LENGTH_INDEFINITE)
		snackbar.view.layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
		snackbar.view.minimumWidth = 300
		(snackbar.view.layoutParams as FrameLayout.LayoutParams).gravity =
			Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
		snackbar.show()
		lifecycleScope.launch(Dispatchers.IO) {
			Cache.sync({
				handler.post {
					snackbar.setText("Sync: $it outstanding...")
				}
			}, {
				handler.post {
					handleError(it)
				}
			}, {
				handler.post {
					snackbar.setText("Sync: finished, $it changes")
					snackbar.duration = Snackbar.LENGTH_SHORT
					snackbar.show()
				}
				Cache.getTreeData()
				handler.post {
					refreshTree()
					getNoteFragment().load("root")
					scrollTreeTo("root")
					supportActionBar?.title = "root"
				}
			})
		}
	}

	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		menuInflater.inflate(R.menu.action_bar, findViewById<Toolbar>(R.id.toolbar).menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
		R.id.action_edit -> {
			val t = Toast(this)
			t.setText("Editing is not yet supported!")
			t.show()
			true
		}

		R.id.action_settings -> {
			startActivity(Intent(this, SetupActivity::class.java))
			true
		}

		else -> {
			// If we got here, the user's action was not recognized.
			// Invoke the superclass to handle it.
			super.onOptionsItemSelected(item)
		}
	}


	public fun scrollTreeTo(noteId: String) {
		tree!!.select(noteId)
		val pos = Cache.getBranchPosition(noteId) ?: return
		(binding.treeList.layoutManager!! as LinearLayoutManager).scrollToPositionWithOffset(pos, 5)
	}

	public fun navigateTo(note: Note) {
		// make sure note is visible
		val path = Cache.getNotePath(note.id)
		var expandedAny = false
		for (n in path) {
			Log.i(TAG, "in branch ${n.note} ${n.expanded}")
			if (!n.expanded) {
				Cache.toggleBranch(n)
				expandedAny = true
			}
		}
		if (expandedAny) {
			refreshTree()
		}
		tree!!.select(note.id)
		supportFragmentManager.beginTransaction()
			.replace(R.id.fragment_container, NoteFragment(note.id))
			.addToBackStack(null)
			.commit()
		binding.drawerLayout.closeDrawers()
		supportActionBar?.title = note.title
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
}