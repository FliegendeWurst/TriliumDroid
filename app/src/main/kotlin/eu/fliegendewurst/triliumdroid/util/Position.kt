package eu.fliegendewurst.triliumdroid.util

class Position(val x: Float, val y: Float) {
	fun distance(other: Position): Float {
		return (x - other.x) * (x - other.x) + (y - other.y) * (y - other.y)
	}

	fun add(other: Position): Position {
		return Position(x + other.x, y + other.y)
	}

	fun subtract(other: Position): Position {
		return Position(x - other.x, y - other.y)
	}

	fun scale(factor: Float): Position {
		return Position(x * factor, y * factor)
	}
}