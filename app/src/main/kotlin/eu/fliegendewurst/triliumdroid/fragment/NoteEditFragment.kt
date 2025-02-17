package eu.fliegendewurst.triliumdroid.fragment

import android.content.Context
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import eu.fliegendewurst.triliumdroid.Cache
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.databinding.FragmentNoteEditBinding
import eu.fliegendewurst.triliumdroid.dialog.JumpToNoteDialog
import kotlinx.coroutines.runBlocking
import org.wordpress.aztec.Aztec
import org.wordpress.aztec.AztecAttributes
import org.wordpress.aztec.ITextFormat
import org.wordpress.aztec.plugins.IToolbarButton
import org.wordpress.aztec.spans.AztecURLSpan
import org.wordpress.aztec.toolbar.AztecToolbar
import org.wordpress.aztec.toolbar.IAztecToolbarClickListener
import org.wordpress.aztec.toolbar.IToolbarAction
import org.wordpress.aztec.toolbar.ToolbarActionType


class NoteEditFragment : Fragment(R.layout.fragment_note_edit),
	IAztecToolbarClickListener, NoteRelatedFragment {
	companion object {
		private const val TAG: String = "NoteEditFragment"
	}

	private lateinit var binding: FragmentNoteEditBinding
	private var id: String? = null

	fun loadLater(id: String) {
		this.id = id
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentNoteEditBinding.inflate(inflater, container, false)
		id = if (id == null) {
			savedInstanceState?.getString("NODE_ID")
		} else {
			id
		}
		return binding.root
	}

	override fun onResume() {
		super.onResume()

		if (id != null) {
			val note = Cache.getNoteWithContent(id!!) ?: return
			val content = note.content?.decodeToString() ?: return
			Aztec.with(binding.visual, binding.source, binding.formattingToolbar, this)
				.addPlugin(object : IToolbarButton {
					override val action: IToolbarAction
						get() = object : IToolbarAction {
							override val actionType: ToolbarActionType
								get() = ToolbarActionType.OTHER
							override val buttonDrawableRes: Int
								get() = R.drawable.bx_link_alt
							override val buttonId: Int
								get() = R.id.button_inline_link
							override val textFormats: Set<ITextFormat>
								get() = setOf()
						}

					override val context: Context
						get() = requireContext()

					override fun inflateButton(parent: ViewGroup) {
						LayoutInflater.from(context).inflate(R.layout.button_inline_link, parent)
					}

					override fun toggle() {
						val start = binding.visual.selectionStart
						val end = binding.visual.selectionEnd
						val prev = binding.visual.getSelectedText()
						JumpToNoteDialog.showDialogReturningNote(
							requireContext() as MainActivity,
							R.string.dialog_select_note
						) {
							val url = "#${it.note}"
							val builder = SpannableStringBuilder(prev)
							// TODO: set data-note-path?
							val newSpan = AztecURLSpan(url, AztecAttributes())
							builder.setSpan(newSpan, 0, 0, Spannable.SPAN_MARK_MARK)
							builder.setSpan(
								newSpan,
								0,
								prev.length,
								Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
							)
							binding.visual.editableText.replace(
								start,
								end,
								builder,
								0,
								builder.length
							)
							runBlocking {
								Cache.addInternalLink(note, it.note)
							}
						}
					}

					override fun toolbarStateAboutToChange(toolbar: AztecToolbar, enable: Boolean) {
						toolbar.findViewById<View>(R.id.button_inline_link).isEnabled = enable
					}
				})
			binding.visual.setCalypsoMode(false)
			binding.source.displayStyledAndFormattedHtml(content)
			binding.visual.fromHtml(content)
		}
	}

	override fun onStop() {
		super.onStop()

		// save to database
		val content = binding.visual.toFormattedHtml()
		if (id != null) {
			Cache.setNoteContent(id!!, content)
		}
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putString("NOTE_ID", id)
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

	override fun getNoteId(): String? {
		return id
	}
}
