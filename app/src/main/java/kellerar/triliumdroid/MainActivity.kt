package kellerar.triliumdroid

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import kellerar.triliumdroid.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
	private lateinit var binding: ActivityMainBinding

	companion object {
		private const val TAG = "MainActivity"
		var tree: TreeItemAdapter? = null
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		val handler = Handler(applicationContext.mainLooper)

		Cache.initializeDatabase(applicationContext)

		val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
		val adapter = TreeItemAdapter {
			navigateTo(it.note)
		}
		binding.treeList.adapter = adapter
		tree = adapter

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
							getNoteFragment().load("root", true)
							scrollTreeTo("root")
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

	public fun scrollTreeTo(noteId: String) {
		tree!!.select(noteId)
		val pos =Cache.branchPosition[noteId] ?: return
		(binding.treeList.layoutManager!! as LinearLayoutManager).scrollToPositionWithOffset(pos, 5)
		//val treeItem = binding.treeList.getChildAt(pos)
	}

	public fun navigateTo(noteId: String) {
		tree!!.select(noteId)
		supportFragmentManager.beginTransaction()
			.replace(R.id.fragment_container, NoteFragment(noteId, true))
			.addToBackStack(null)
			.commit()
	}

	override fun onDestroy() {
		super.onDestroy()
		Cache.closeDatabase()
	}

	private fun getNoteFragment(): NoteFragment {
		val hostFragment =
			supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
		val frags = hostFragment.childFragmentManager.fragments
		return frags[0] as NoteFragment
	}
}