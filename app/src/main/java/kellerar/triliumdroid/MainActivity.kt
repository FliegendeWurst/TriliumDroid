package kellerar.triliumdroid

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.widget.addTextChangedListener
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kellerar.triliumdroid.databinding.ActivityMainBinding
import java.util.TreeMap


class MainActivity : AppCompatActivity() {
	private lateinit var binding: ActivityMainBinding
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
		val handler = Handler(applicationContext.mainLooper)

		val toolbar = findViewById<Toolbar>(R.id.toolbar)
		setSupportActionBar(toolbar)

		Cache.initializeDatabase(applicationContext)

		val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
		val adapter = TreeItemAdapter {
			navigateTo(Cache.getNote(it.note)!!)
		}
		binding.treeList.adapter = adapter
		tree = adapter

		binding.fab.setOnClickListener {
			val dialog = AlertDialog.Builder(this)
				.setTitle(R.string.jump_to_dialog)
				.setView(R.layout.dialog_jump)
				.create()
			dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
			dialog.show()
			val input = dialog.findViewById<EditText>(R.id.jump_input)!!
			val list = dialog.findViewById<RecyclerView>(R.id.jump_to_list)!!
			val adapter2 = TreeItemAdapter {
				dialog.dismiss()
				scrollTreeTo(it.note)
				navigateTo(Cache.getNote(it.note)!!)
			}
			list.adapter = adapter2
			input.requestFocus()
			input.addTextChangedListener {
				val searchString = input.text.toString()
				if (searchString.length < 3) {
					adapter2.submitList(emptyList())
					return@addTextChangedListener
				}
				val results = Cache.getJumpToResults(searchString)
				val m = TreeMap<Int, Branch>()
				val stuff = results.map {
					Pair(
						Branch(JUMP_TO_NOTE_ENTRY, it.id, null, 0, null, false, m),
						0
					)
				}.toList()
				if (adapter2.currentList != stuff) {
					adapter2.submitList(stuff)
				}
				/* TODO: dispatch sql query on I/O thread
				lifecycleScope.launch(Dispatchers.IO) {

				}
				 */
			}
		}

		if (prefs.getString("hostname", null) == null) {
			Log.i(TAG, "starting setup!")
			val intent = Intent(this, SetupActivity::class.java)
			startActivity(intent)
		} else {
			ConnectionUtil.setup(this, prefs) {
				handler.post {
					Cache.sync(this) {
						Cache.getTreeData()
						handler.post {
							val items = Cache.getTreeList("root", 0)
							Log.i(TAG, "about to show ${items.size} tree items")
							tree!!.submitList(items)
							getNoteFragment().load("root")
							scrollTreeTo("root")
							supportActionBar?.title = "root"
						}
					}
				}
			}

			try {
				binding.drawerLayout.openDrawer(GravityCompat.START)
			} catch (t: Throwable) {
				Log.e("Main", "fatality!", t)
			}
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