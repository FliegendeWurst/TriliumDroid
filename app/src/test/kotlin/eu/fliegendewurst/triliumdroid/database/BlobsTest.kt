package eu.fliegendewurst.triliumdroid.database

import org.junit.Assert.assertEquals
import org.junit.Test

class BlobsTest {
	@Test
	fun contentHashIsCorrect() {
		assertEquals("z4PhNX7vuL3xVChQ1m2A", Blobs.calcHash(null, false).id)
		assertEquals("z4PhNX7vuL3xVChQ1m2A", Blobs.calcHash(byteArrayOf(), false).id)
		assertEquals(
			"7SsREyHyFYSXB6DcrHza", Blobs.calcHash(
				("<div>\n" +
						"          <div>\n" +
						"            <p>This is a place I use to put notes waiting for better categorization</p>\n" +
						"          </div>\n" +
						"        </div>").encodeToByteArray(), false
			).id
		)
	}
}
