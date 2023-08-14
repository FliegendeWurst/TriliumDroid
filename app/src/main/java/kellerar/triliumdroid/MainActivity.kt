package kellerar.triliumdroid

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import kellerar.triliumdroid.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
	companion object {
		private const val TAG = "MainActivity"
		var tree: TreeItemAdapter? = null
	}
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		val handler = Handler(applicationContext.mainLooper)

		Cache.initializeDatabase(applicationContext)

		val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
		if (prefs.getString("hostname", null) == null) {
			Log.i(TAG, "starting setup!")
			val intent = Intent(this, SetupActivity::class.java)
			startActivity(intent)
		} else {
			ConnectionUtil.setup(this, prefs) {
				handler.post {
					Cache.sync(this) {
						handler.post {
							getNoteFragment().load("root", true)
						}
					}
				}
			}

			val adapter = TreeItemAdapter {
				supportFragmentManager.beginTransaction()
					.replace(R.id.fragment_container, NoteFragment(it.note, true))
					.addToBackStack(null)
					.commit()
			}
			binding.treeList.adapter = adapter
			tree = adapter

			try {
				binding.drawerLayout.openDrawer(GravityCompat.START)
			} catch (t: Throwable) {
				Log.e("Main", "fatality!", t)
			}
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		Cache.closeDatabase()
	}

	private fun getNoteFragment(): NoteFragment {
		val hostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
		val frags = hostFragment.childFragmentManager.fragments
		return frags[0] as NoteFragment
	}
}