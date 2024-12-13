package eu.fliegendewurst.triliumdroid.util

class Position(val x: Float, val y: Float) {
	fun distance(other: Position): Float {
		return (x - other.x) * (x - other.x) + (y - other.y) * (y - other.y)
	}
}