package eu.fliegendewurst.triliumdroid.fragment

import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import eu.fliegendewurst.triliumdroid.Cache
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.databinding.FragmentNavigationBinding
import eu.fliegendewurst.triliumdroid.databinding.ItemNavigationButtonBinding
import eu.fliegendewurst.triliumdroid.service.Icon
import eu.fliegendewurst.triliumdroid.util.ListRecyclerAdapter
import kotlinx.coroutines.launch


/**
 * After creating the fragment, call [NavigationFragment.showFor]
 */
class NavigationFragment : Fragment(R.layout.fragment_navigation) {
	companion object {
		private const val TAG: String = "NavigationFragment"

		private const val FLAG_LAST: Int = 1
	}

	private lateinit var binding: FragmentNavigationBinding
	private lateinit var adapter: ListRecyclerAdapter<Entry, ItemNavigationButtonBinding>
	private lateinit var adapter2: ListRecyclerAdapter<Entry, ItemNavigationButtonBinding>

	private var note: Note? = null
	private var branch: Branch? = null

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentNavigationBinding.inflate(inflater, container, false)

		val rootWidth = container!!.width - binding.root.paddingStart - binding.root.paddingEnd
		Log.d(TAG, "measured root width = $rootWidth")

		val layoutManager = LinearLayoutManager(context)
		layoutManager.stackFromEnd = true
		layoutManager.orientation = VERTICAL
		binding.navigationList.setLayoutManager(layoutManager)
		binding.navigationList.setHasFixedSize(true)
		binding.navigationList.isNestedScrollingEnabled = false
		binding.navigationList.itemAnimator = null

		adapter = ListRecyclerAdapter({ view, x ->
			view.navigationButtonIcon.text = Icon.getUnicodeCharacter(x.icon)
			view.navigationButtonLabel.text = x.title

			view.clickArea.setOnClickListener {
				showForInternal(x.note, x.branch)
			}
		}) { parent ->
			return@ListRecyclerAdapter ItemNavigationButtonBinding.inflate(
				LayoutInflater.from(
					parent.context
				), parent, false
			)
		}
		binding.navigationList.adapter = adapter


		val layoutManager2 = LinearLayoutManager(context)
		layoutManager2.stackFromEnd = true
		layoutManager2.orientation = RecyclerView.HORIZONTAL
		binding.navigationListBottom.setLayoutManager(layoutManager2)
		binding.navigationListBottom.setHasFixedSize(true)
		binding.navigationListBottom.isNestedScrollingEnabled = false
		binding.navigationListBottom.itemAnimator = null
		val bg = resources.getColor(R.color.background, null)
		val fg = resources.getColor(R.color.foreground, null)
		adapter2 = ListRecyclerAdapter({ view, x ->
			view.root.layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT

			view.navigationButtonFiller.layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
			view.navigationButtonFiller.text = ">"
			(view.navigationButtonFiller.layoutParams as LinearLayout.LayoutParams).weight = 0f
			view.clickArea.setBackgroundColor(bg)

			view.navigationButtonIcon.setTextColor(fg)
			view.navigationButtonLabel.setTextColor(fg)

			view.navigationButtonIcon.text = Icon.getUnicodeCharacter(x.icon)
			view.navigationButtonLabel.text = x.title
			if (x.flags == FLAG_LAST) {
				view.navigationButtonLabel.paintFlags =
					view.navigationButtonLabel.paintFlags or Paint.UNDERLINE_TEXT_FLAG
			} else {
				view.navigationButtonLabel.paintFlags =
					view.navigationButtonLabel.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
			}

			view.clickArea.setOnClickListener {
				if (x.flags == FLAG_LAST) {
					(this.activity as MainActivity).navigateTo(x.note, x.branch)
					return@setOnClickListener
				}
				showForInternal(x.note, x.branch)
			}
		}) { parent ->
			return@ListRecyclerAdapter ItemNavigationButtonBinding.inflate(
				LayoutInflater.from(
					parent.context
				), parent, false
			)
		}
		binding.navigationListBottom.adapter = adapter2

		if (note != null) {
			showForInternal(note!!, branch)
		}

		return binding.root
	}

	fun showFor(note: Note, branch: Branch?) {
		this.note = note
		this.branch = branch
	}

	private fun showForInternal(note: Note, branch: Branch?) {
		this.note = note
		this.branch = branch

		val main = this.activity as MainActivity
		lifecycleScope.launch {
			val notes = note.computeChildren()
			if (notes.isEmpty()) {
				main.navigateTo(note, branch)
				return@launch
			}
			val entries = notes.map {
				val noteHere = Cache.getNote(it.note)!!
				Entry(noteHere.icon(), noteHere.title, noteHere, it, 0)
			}.asReversed()
			adapter.submitList(entries)
			val entries2 = Cache.getNotePath(note.id).map {
				val note2 = Cache.getNote(it.note)!!
				Entry(note2.icon(), note2.title, note2, it, 0)
			}
			entries2.first().flags = FLAG_LAST
			adapter2.submitList(entries2.asReversed())
			binding.navigationListBottom.stopScroll()
			binding.navigationListBottom.smoothScrollBy(5000, 0)
		}
	}

	data class Entry(
		val icon: String,
		val title: String,
		val note: Note,
		val branch: Branch,
		var flags: Int
	)
}
