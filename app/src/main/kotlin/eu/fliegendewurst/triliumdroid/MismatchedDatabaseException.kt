package eu.fliegendewurst.triliumdroid

object MismatchedDatabaseException : IllegalStateException("tried to sync different database") {
	private fun readResolve(): Any = MismatchedDatabaseException
}