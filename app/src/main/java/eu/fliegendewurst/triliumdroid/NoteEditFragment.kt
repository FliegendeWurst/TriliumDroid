package eu.fliegendewurst.triliumdroid

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import eu.fliegendewurst.triliumdroid.databinding.FragmentNoteEditBinding
import org.wordpress.aztec.Aztec
import org.wordpress.aztec.ITextFormat
import org.wordpress.aztec.toolbar.IAztecToolbarClickListener

class NoteEditFragment(private val id: String) : Fragment(R.layout.fragment_note_edit),
	IAztecToolbarClickListener {
	companion object {
		private const val TAG: String = "NoteEditFragment"
	}

	private lateinit var binding: FragmentNoteEditBinding

	@SuppressLint("SetJavaScriptEnabled")
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentNoteEditBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onResume() {
		super.onResume()

		Aztec.with(binding.visual, binding.source, binding.formattingToolbar, this)
		binding.visual.fromHtml(String(Cache.getNoteWithContent(id)?.content ?: return))
	}

	override fun onStop() {
		super.onStop()

		// save to database
		val content = binding.visual.toFormattedHtml()
		Cache.setNoteContent(id, content)
	}

	override fun onToolbarCollapseButtonClicked() {
		Log.e(TAG, "onToolbarCollapseButtonClicked")
	}

	override fun onToolbarExpandButtonClicked() {
		Log.e(TAG, "onToolbarExpandButtonClicked")
	}

	override fun onToolbarFormatButtonClicked(format: ITextFormat, isKeyboardShortcut: Boolean) {
		Log.e(TAG, "onToolbarFormatButtonClicked")
	}

	override fun onToolbarHeadingButtonClicked() {
		Log.e(TAG, "onToolbarHeadingButtonClicked")
	}

	override fun onToolbarHtmlButtonClicked() {
		Log.e(TAG, "onToolbarHtmlButtonClicked")
	}

	override fun onToolbarListButtonClicked() {
		Log.e(TAG, "onToolbarListButtonClicked")
	}

	override fun onToolbarMediaButtonClicked(): Boolean {
		Log.e(TAG, "onToolbarMediaButtonClicked")
		return false
	}
}