package eu.fliegendewurst.triliumdroid.notetype

import android.content.Context
import eu.fliegendewurst.triliumdroid.util.Resources

/**
 * Note type "mindMap"
 */
class MindMap(jsonString: String) {
	fun template(context: Context): String {
		return Resources.mindMap_TPL(context)
		// TODO: add javascript to drive the mess?
	}
}
