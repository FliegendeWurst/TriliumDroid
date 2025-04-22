package eu.fliegendewurst.triliumdroid.fragment

import eu.fliegendewurst.triliumdroid.data.NoteId

interface NoteRelatedFragment {
	fun getNoteId(): NoteId?
}
