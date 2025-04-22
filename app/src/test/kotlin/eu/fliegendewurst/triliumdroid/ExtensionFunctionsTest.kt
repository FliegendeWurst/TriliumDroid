package eu.fliegendewurst.triliumdroid

import eu.fliegendewurst.triliumdroid.database.boolToIntValue
import eu.fliegendewurst.triliumdroid.database.intValueToBool
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionFunctionsTest {
	@Test
	fun intToBool() {
		assertEquals(true, 1.intValueToBool())
		assertEquals(false, 0.intValueToBool())
	}

	@Test
	fun boolToInt() {
		assertEquals(1, true.boolToIntValue())
		assertEquals(0, false.boolToIntValue())
	}
}
