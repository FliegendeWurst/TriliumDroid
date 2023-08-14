package kellerar.triliumdroid

import org.json.JSONObject

class Note {
	public var id: String
	public val mime: String
	public var title: String
	public var content: ByteArray? = null

	constructor(obj: JSONObject) {
		//Log.i("Note", obj.toString())
		id = obj.getString("noteId")
		mime = obj.getString("mime")
		title = obj.getString("title")
		content = obj.optString("content", "").toByteArray()
	}

	constructor(id: String, mime: String, title: String) {
		this.id = id
		this.mime = mime
		this.title = title
	}
}