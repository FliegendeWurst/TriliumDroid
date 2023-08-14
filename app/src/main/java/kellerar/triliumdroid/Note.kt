package kellerar.triliumdroid

class Note(public var id: String, public val mime: String, public var title: String) {
	public var content: ByteArray? = null
}