package eu.fliegendewurst.triliumdroid

import android.webkit.JavascriptInterface
import eu.fliegendewurst.triliumdroid.data.Note

class FrontendNote(private val note: Note) {
	@JavascriptInterface
	fun noteId(): String {
		return note.id
	}

	/**
	 * Gives all possible note paths leading to this note. Paths containing search note are ignored (could form cycles)
	 *
	 * @returns {string[][]} - array of notePaths (each represented by array of noteIds constituting the particular note path)
	 */
	@JavascriptInterface
	fun getAllNotePaths() {
	}

	@JavascriptInterface
	fun getAttribute(type: String?, name: String?): FrontendAttribute? {
		TODO("xxx")
	}

	/**
	 * @param {string} [type] - (optional) attribute type to filter
	 * @param {string} [name] - (optional) attribute name to filter
	 * @returns {FAttribute[]} all note's attributes, including inherited ones
	 */
	@JavascriptInterface
	fun getAttributes(type: String?, name: String?): List<FrontendAttribute> {
		return emptyList()
	}

	/**
	 * @param {AttributeType} type - attribute type (label, relation, etc.)
	 * @param {string} name - attribute name
	 * @returns {string} attribute value of the given type and name or null if no such attribute exists.
	 */
	@JavascriptInterface
	fun getAttributeValue(type: String?, name: String?): String? {
		val attr = this.getAttribute(type, name)

		return attr?.attribute?.value()
	}
}