package eu.fliegendewurst.triliumdroid.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.marginEnd
import androidx.fragment.app.Fragment
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import eu.fliegendewurst.triliumdroid.Cache
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.data.Branch
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.databinding.FragmentNavigationBinding
import eu.fliegendewurst.triliumdroid.databinding.ItemNavigationButtonBinding
import eu.fliegendewurst.triliumdroid.service.Icon
import eu.fliegendewurst.triliumdroid.util.ListRecyclerAdapter


/**
 * After creating the fragment, call [NavigationFragment.showFor]
 */
class NavigationFragment : Fragment(R.layout.fragment_navigation) {
	companion object {
		private const val TAG: String = "NavigationFragment"

		private const val FLAG_LAST: Int = 1
		private const val FLAG_PARENT: Int = 2
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

		val layoutManager = FlexboxLayoutManager(context)
		layoutManager.flexDirection = FlexDirection.COLUMN
		layoutManager.justifyContent = JustifyContent.FLEX_END
		layoutManager.alignItems = AlignItems.FLEX_END
		binding.navigationList.setLayoutManager(layoutManager)

		adapter = ListRecyclerAdapter({ view, x ->
			view.navigationButtonIcon.text = Icon.getUnicodeCharacter(x.icon)
			view.navigationButtonLabel.text = x.title

			view.root.setOnClickListener {
				showForInternal(x.note, x.branch)
			}

			// right-align items
			val params = view.root.layoutParams as FlexboxLayoutManager.LayoutParams
			view.root.measure(0, 0)
			params.leftMargin =
				binding.navigationList.width - view.root.measuredWidth - view.root.marginEnd
			view.root.setLayoutParams(params)
		}) { parent ->
			return@ListRecyclerAdapter ItemNavigationButtonBinding.inflate(
				LayoutInflater.from(
					parent.context
				), parent, false
			)
		}
		binding.navigationList.adapter = adapter


		val layoutManager2 = FlexboxLayoutManager(context)
		layoutManager2.flexDirection = FlexDirection.ROW
		layoutManager2.justifyContent = JustifyContent.FLEX_END
		layoutManager2.flexWrap = FlexWrap.NOWRAP
		binding.navigationListBottom.setLayoutManager(layoutManager2)
		adapter2 = ListRecyclerAdapter({ view, x ->
			view.navigationButtonIcon.text = Icon.getUnicodeCharacter(x.icon)
			view.navigationButtonLabel.text = x.title

			view.root.setOnClickListener {
				if (x.flags == FLAG_LAST) {
					(this.activity as MainActivity).navigateTo(x.note, x.branch)
					return@setOnClickListener
				}
				showForInternal(x.note, x.branch)
			}

			// make sure items are not shrunk...
			val params = view.root.layoutParams as FlexboxLayoutManager.LayoutParams
			params.flexShrink = 0.0f
			view.root.setLayoutParams(params)
		}) { parent ->
			return@ListRecyclerAdapter ItemNavigationButtonBinding.inflate(
				LayoutInflater.from(
					parent.context
				), parent, false
			)
		}
		binding.navigationListBottom.adapter = adapter2
		binding.navigationListBottomHolder.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
			binding.navigationListBottomHolder.fullScroll(View.FOCUS_RIGHT)
		}

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

		val notes = note.computeChildren()
		if (notes.isEmpty()) {
			(this.activity as MainActivity).navigateTo(note, branch)
			return
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
	}

	data class Entry(
		val icon: String,
		val title: String,
		val note: Note,
		val branch: Branch,
		var flags: Int
	)
}
