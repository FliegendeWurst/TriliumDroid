package eu.fliegendewurst.triliumdroid.service

import org.junit.Test


import org.junit.Assert.*
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DateNotesServiceTest {
	@Test
	fun dayFormat() {
		val date = OffsetDateTime.of(2023, 8, 28, 5, 0, 0, 0, ZoneOffset.UTC)
		assertEquals("28 - Monday", DateNotes.DAY.format(date))
		assertEquals("08 - August", DateNotes.MONTH.format(date))
	}
}