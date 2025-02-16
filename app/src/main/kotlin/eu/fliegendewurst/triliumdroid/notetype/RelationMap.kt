package eu.fliegendewurst.triliumdroid.notetype

import eu.fliegendewurst.triliumdroid.util.Position
import org.json.JSONObject

/**
 * Note type "relationMap"
 */
class RelationMap(json: JSONObject) {
	private var notes: MutableMap<String, Position> = mutableMapOf()
	private var transformXY: Position = Position(0.0f, 0.0f)
	private var transformScale: Float = 1.0f

	init {
		val notes = json.getJSONArray("notes")
		for (i in 0 until notes.length()) {
			val note = notes[i] as JSONObject
			val id = note.getString("noteId")
			val x = note.getDouble("x").toFloat()
			val y = note.getDouble("y").toFloat()
			this.notes[id] = Position(x, y)
		}
		val transform = json.getJSONObject("transform")
		val scale = transform.getDouble("scale").toFloat()
		val x = transform.getDouble("x").toFloat()
		val y = transform.getDouble("y").toFloat()
		transformScale = scale
		transformXY = Position(x, y)
	}
}
