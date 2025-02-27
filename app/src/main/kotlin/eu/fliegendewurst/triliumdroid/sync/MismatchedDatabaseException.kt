package eu.fliegendewurst.triliumdroid.sync

object MismatchedDatabaseException : IllegalStateException("tried to sync different database") {
	private fun readResolve(): Any = MismatchedDatabaseException
}
