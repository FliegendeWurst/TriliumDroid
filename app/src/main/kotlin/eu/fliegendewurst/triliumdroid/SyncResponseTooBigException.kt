package eu.fliegendewurst.triliumdroid

object SyncResponseTooBigException : Exception() {
	override val message: String
		get() = "sync response too big"
}