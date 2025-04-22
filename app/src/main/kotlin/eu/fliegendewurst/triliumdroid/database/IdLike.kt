package eu.fliegendewurst.triliumdroid.database

interface IdLike {
	fun rawId(): String
	fun columnName(): String
	fun tableName(): String
}
