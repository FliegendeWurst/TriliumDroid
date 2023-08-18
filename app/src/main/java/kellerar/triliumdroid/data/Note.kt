package kellerar.triliumdroid.data

class Note(public var id: String, public val mime: String, public var title: String) {
	public var content: ByteArray? = null
	public var labels: List<Label>? = null
}