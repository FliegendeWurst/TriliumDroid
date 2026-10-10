package eu.fliegendewurst.triliumdroid.sync

import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import android.util.Base64OutputStream
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FilterOutputStream
import java.security.MessageDigest

class SyncPullWriterTest {
	@Test
	fun supportsTokenAndRecentNoteEntities() {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val directory = File(context.cacheDir, "sync-metadata-test-${System.nanoTime()}").apply { mkdirs() }
		val db = SQLiteDatabase.create(null)
		try {
			db.execSQL("CREATE TABLE entity_changes (changeId TEXT PRIMARY KEY)")
			db.execSQL("CREATE TABLE options (name TEXT PRIMARY KEY, value TEXT, isSynced INT, utcDateModified TEXT)")
			db.execSQL("CREATE TABLE etapi_tokens (etapiTokenId TEXT PRIMARY KEY, name TEXT)")
			db.execSQL("CREATE TABLE recent_notes (noteId TEXT PRIMARY KEY, notePath TEXT)")
			val page = """{"entityChanges":[{"entityChange":{"entityName":"etapi_tokens","changeId":"a"},"entity":{"etapiTokenId":"a","name":"test"}},{"entityChange":{"entityName":"recent_notes","changeId":"b"},"entity":{"noteId":"root","notePath":"root"}}],"lastEntityChangeId":2,"outstandingPullCount":0}"""
			SyncPullWriter.apply(db, page.byteInputStream(), directory)
			for (table in listOf("etapi_tokens", "recent_notes")) {
				db.rawQuery("SELECT count(*) FROM $table", null).use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
			}
		} finally {
			db.close()
			directory.deleteRecursively()
		}
	}

	@Test
	fun largeBinaryIsStoredExactlyAndFailedPageRollsBack() {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val directory = File(context.cacheDir, "sync-writer-test-${System.nanoTime()}").apply { mkdirs() }
		val db = SQLiteDatabase.openOrCreateDatabase(File(directory, "test.db"), null)
		try {
			db.execSQL("CREATE TABLE blobs (blobId TEXT PRIMARY KEY, content BLOB)")
			db.execSQL("CREATE TABLE entity_changes (changeId TEXT PRIMARY KEY)")
			db.execSQL("CREATE TABLE options (name TEXT PRIMARY KEY, value TEXT, isSynced INT, utcDateModified TEXT)")
			val response = File(directory, "response.json")
			val digest = MessageDigest.getInstance("SHA-256")
			val block = ByteArray(8192) { it.toByte() }
			val size = 391_643_136
			response.outputStream().buffered().use { output ->
				output.write("""{"entityChanges":[{"entityChange":{"entityName":"blobs","changeId":"large"},"entity":{"blobId":"large","content":"""".toByteArray())
				val encoded = Base64OutputStream(object : FilterOutputStream(output) {
					override fun close() { flush() }
				}, Base64.NO_WRAP)
				var left = size
				while (left > 0) {
					val count = minOf(left, block.size)
					encoded.write(block, 0, count)
					digest.update(block, 0, count)
					left -= count
				}
				encoded.close()
				output.write(""""}}],"lastEntityChangeId":42,"outstandingPullCount":0}""".toByteArray())
			}
			response.inputStream().use { SyncPullWriter.apply(db, it, directory) }
			db.rawQuery("SELECT length(content), typeof(content) FROM blobs WHERE blobId='large'", null).use {
				assertTrue(it.moveToFirst())
				assertEquals(size, it.getInt(0))
				assertEquals("blob", it.getString(1))
			}
			val actual = MessageDigest.getInstance("SHA-256")
			var offset = 1
			while (offset <= size) {
				db.rawQuery("SELECT substr(content, ?, 1048576) FROM blobs WHERE blobId='large'", arrayOf(offset.toString())).use {
					assertTrue(it.moveToFirst())
					val bytes = it.getBlob(0)
					actual.update(bytes)
					offset += bytes.size
				}
			}
			assertArrayEquals(digest.digest(), actual.digest())
			val broken = """{"entityChanges":[{"entityChange":{"entityName":"blobs","changeId":"new"},"entity":{"blobId":"new","content":"AAEC"}},"""
			assertThrows(Exception::class.java) {
				SyncPullWriter.apply(db, broken.byteInputStream(), directory)
			}
			db.rawQuery("SELECT count(*) FROM blobs", null).use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
			db.rawQuery("SELECT value FROM options WHERE name='lastSyncedPull'", null).use {
				it.moveToFirst(); assertEquals("42", it.getString(0))
			}
			assertTrue(directory.listFiles()!!.none { it.name.startsWith("sync-content-") })
		} finally {
			db.close()
			directory.deleteRecursively()
		}
	}
}
