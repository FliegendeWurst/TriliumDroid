package eu.fliegendewurst.triliumdroid.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import eu.fliegendewurst.triliumdroid.FrontendBackendApi
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.data.NoteId
import eu.fliegendewurst.triliumdroid.database.Notes
import eu.fliegendewurst.triliumdroid.databinding.FragmentNoteEditBinding
import eu.fliegendewurst.triliumdroid.fragment.note.NoteFragment.Companion.WEBVIEW_DOMAIN
import eu.fliegendewurst.triliumdroid.fragment.note.NoteWebViewClient
import eu.fliegendewurst.triliumdroid.util.MyWebChromeClient
import kotlinx.coroutines.runBlocking


class NoteEditFragment : Fragment(R.layout.fragment_note_edit), NoteRelatedFragment {
	companion object {
		private const val TAG: String = "NoteEditFragment"
	}

	private lateinit var binding: FragmentNoteEditBinding
	private var id: NoteId? = null

	fun loadLater(id: NoteId) {
		this.id = id
	}

	@SuppressLint("SetJavaScriptEnabled")
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentNoteEditBinding.inflate(inflater, container, false)
		id = if (id == null) {
			val rawId = savedInstanceState?.getString("NODE_ID")
			if (rawId != null) {
				NoteId(rawId)
			} else {
				null
			}
		} else {
			id
		}
		binding.webviewEditable.settings.javaScriptEnabled = true
		binding.webviewEditable.addJavascriptInterface(
			FrontendBackendApi(this, this.requireContext(), Handler(requireContext().mainLooper)),
			"api"
		)
		binding.webviewEditable.webChromeClient = MyWebChromeClient(
			{ (this@NoteEditFragment.activity as MainActivity?) },
			{ })
		binding.webviewEditable.webViewClient = NoteWebViewClient({
			runBlocking {
				if (id != null) {
					Notes.getNote(id!!.id)
				} else {
					null
				}
			}
		}, { null }, { null }, { activity as MainActivity? }, { })
		if (id != null) {
			binding.webviewEditable.loadUrl("${WEBVIEW_DOMAIN}note-editable#${id!!.id}")
		}
		return binding.root
	}

	override fun onResume() {
		super.onResume()

		/*
		if (id != null) {
			val note = runBlocking { Notes.getNoteWithContent(id!!) } ?: return
			val content = note.content()?.decodeToString() ?: return
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
						var prev = binding.visual.getSelectedText()
						JumpToNoteDialog.showDialogReturningNote(
							requireContext() as MainActivity,
							R.string.dialog_select_note
						) {
							if (prev.isBlank()) {
								val noteLinked = runBlocking { Notes.getNote(it.note)!! }
								prev = noteLinked.title()
							}
							// TODO: make this the full path #root/note1/note2/it.note
							val url = "#${it.note}"
							val builder = SpannableStringBuilder(prev)
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
								Notes.addInternalLink(note, it.note)
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
		}*/
	}

	override fun onStop() {
		binding.webviewEditable.evaluateJavascript("scheduledUpdate()") {}
		super.onStop()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putString("NOTE_ID", id?.id)
	}

	override fun getNoteId(): String? {
		return id?.id
	}
}
