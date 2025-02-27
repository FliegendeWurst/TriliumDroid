package eu.fliegendewurst.triliumdroid.sync

object SyncResponseTooBigException : Exception() {
	private fun readResolve(): Any = SyncResponseTooBigException
	override val message: String
		get() = "sync response too big"
}
