package eu.fliegendewurst.triliumdroid.sync

class MismatchedDatabaseException : IllegalStateException("tried to sync different database") {
	private fun readResolve(): Any = MismatchedDatabaseException()
}
