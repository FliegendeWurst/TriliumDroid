package eu.fliegendewurst.triliumdroid.service

import org.junit.Test


import org.junit.Assert.*
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DateNotesServiceTest {
	@Test
	fun dayFormat() {
		assertEquals("28 - Monday", DateNotesService.DAY.format(OffsetDateTime.of(2023, 8, 28, 5, 0, 0, 0, ZoneOffset.UTC)));
	}
}