package eu.fliegendewurst.triliumdroid.util

/**
 * Source: https://stackoverflow.com/a/63848290/5837178 by sergeych
 */
fun ByteArray.findFirst(sequence: ByteArray, startFrom: Int = 0): Int {
	if (sequence.isEmpty()) {
		return -1
	}
	if (startFrom < 0) throw IllegalArgumentException("startFrom must be non-negative")
	var matchOffset = 0
	var start = startFrom
	var offset = startFrom
	while (offset < size) {
		if (this[offset] == sequence[matchOffset]) {
			if (matchOffset++ == 0) start = offset
			if (matchOffset == sequence.size) return start
		} else
			matchOffset = 0
		offset++
	}
	return -1
}
