package eu.fliegendewurst.triliumdroid.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.core.database.sqlite.transaction
import eu.fliegendewurst.triliumdroid.database.Cache.Versions
import eu.fliegendewurst.triliumdroid.service.Util
import eu.fliegendewurst.triliumdroid.util.Preferences

class CacheDbHelper(context: Context, private val sql: String) :
	SQLiteOpenHelper(context, Versions.DATABASE_NAME, null, Versions.DATABASE_VERSION) {
	companion object {
		private const val TAG = "CacheDbHelper"
		const val MAX_MIGRATION = 2
	}

	override fun onCreate(db: SQLiteDatabase) {
		if (DB.skipNextMigration) {
			Preferences.setDatabaseMigration(MAX_MIGRATION)
			return
		}
		try {
			Log.i(TAG, "creating database ${db.attachedDbs[0].second}")
			sql.split(';').forEach {
				if (it.isBlank()) {
					return@forEach
				}
				try {
					db.execSQL(it)
				} catch (e: SQLiteException) {
					Log.e(TAG, "failure in DB creation ", e)
				}
			}
			db.rawQuery(
				"SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name != 'android_metadata' AND name != 'sqlite_sequence'",
				arrayOf()
			).use {
				if (it.moveToNext()) {
					Log.i(TAG, "successfully created ${it.getInt(0)} tables")
				} else {
					Log.w(TAG, "unable to fetch sqlite_master table data")
				}
			}
			// DB migrations are only for fixups
			Preferences.setDatabaseMigration(MAX_MIGRATION)
		} catch (t: Throwable) {
			Log.e(TAG, "fatal error creating database", t)
		}
	}

	override fun onUpgrade(db: SQLiteDatabase, oldVersionAndroid: Int, newVersion: Int) {
		var oldVersion = oldVersionAndroid
		if (DB.skipNextMigration) {
			db.rawQuery("SELECT value FROM options WHERE name = 'dbVersion'", arrayOf())
				.use {
					if (it.moveToNext()) {
						oldVersion = it.getString(0).toInt()
					} else {
						Log.e(TAG, "failed to get previous database version")
					}
				}
			db.rawQuery("SELECT value FROM options WHERE name = ?", arrayOf("documentSecret"))
				.use {
					if (it.moveToFirst()) {
						// This is important to ensure that a (later) sync configuration works properly.
						val secret = it.getString(0)
						Preferences.setDocumentSecret(secret)
					} else {
						Log.e(TAG, "failed to get previous document secret")
					}
				}
			// remove now-useless entity_changes
			db.rawQuery("DELETE FROM entity_changes", arrayOf()).close()
			// ETAPI irrelevant for mobile app
			db.rawQuery("DELETE FROM etapi_tokens", arrayOf()).close()
			DB.skipNextMigration = false
		}
		try {
			// source: https://github.com/zadam/trilium/tree/master/db/migrations
			// and: https://github.com/TriliumNext/Notes/tree/develop/db/migrations
			db.transaction {
				if (oldVersion < 215 && newVersion >= 215) {
					Log.i(TAG, "migrating to version 215")
					execSQL("CREATE TABLE IF NOT EXISTS \"blobs\" (blobId TEXT NOT NULL, content TEXT DEFAULT NULL, dateModified TEXT NOT NULL, utcDateModified TEXT NOT NULL, PRIMARY KEY(blobId))")
					execSQL("ALTER TABLE notes ADD blobId TEXT DEFAULT NULL")
					execSQL("ALTER TABLE note_revisions ADD blobId TEXT DEFAULT NULL")
				}
				if (oldVersion < 216 && newVersion >= 216) {
					Log.i(TAG, "migrating to version 216")
					val existingBlobIds = mutableSetOf<String>()

					rawQuery(
						"SELECT noteId, content, dateModified, utcDateModified FROM note_contents",
						arrayOf()
					).use {
						while (it.moveToNext()) {
							val noteId = it.getString(0)
							val content = it.getBlob(1)
							val dateModified = it.getString(2)
							val utcDateModified = it.getString(3)

							val blobId = Util.contentHash(content)
							if (!existingBlobIds.contains(blobId)) {
								existingBlobIds.add(blobId)
								execSQL(
									"INSERT INTO blobs VALUES (?, ?, ?, ?)",
									arrayOf(blobId, content, dateModified, utcDateModified)
								)
								execSQL(
									"UPDATE entity_changes SET entityName = 'blobs', entityId = ? WHERE entityName = 'note_contents' AND entityId = ?",
									arrayOf(blobId, noteId)
								)
							} else {
								execSQL(
									"DELETE FROM entity_changes WHERE entityName = 'note_contents' AND entityId = ?",
									arrayOf(noteId)
								)
							}
							execSQL(
								"UPDATE notes SET blobId = ? WHERE noteId = ?",
								arrayOf(blobId, noteId)
							)
						}
					}

					rawQuery(
						"SELECT noteRevisionId, content, utcDateModified FROM note_revision_contents",
						arrayOf()
					).use {
						while (it.moveToNext()) {
							val noteRevisionId = it.getString(0)
							val content = it.getBlob(1)
							val utcDateModified = it.getString(2)

							val blobId = Util.contentHash(content)
							if (!existingBlobIds.contains(blobId)) {
								existingBlobIds.add(blobId)
								execSQL(
									"INSERT INTO blobs VALUES (?, ?, ?, ?)",
									arrayOf(blobId, content, utcDateModified, utcDateModified)
								)
								execSQL(
									"UPDATE entity_changes SET entityName = 'blobs', entityId = ? WHERE entityName = 'note_revision_contents' AND entityId = ?",
									arrayOf(blobId, noteRevisionId)
								)
							} else {
								execSQL(
									"DELETE FROM entity_changes WHERE entityName = 'note_revision_contents' AND entityId = ?",
									arrayOf(noteRevisionId)
								)
							}
							execSQL(
								"UPDATE note_revisions SET blobId = ? WHERE noteRevisionId = ?",
								arrayOf(blobId, noteRevisionId)
							)
						}
					}

					// don't bother counting notes without blobs
					// this database migration will never be used by real users anyhow...
				}
				if (oldVersion < 217 && newVersion >= 217) {
					Log.i(TAG, "migrating to version 217")
					execSQL("DROP TABLE note_contents")
					execSQL("DROP TABLE note_revision_contents")
					execSQL("DELETE FROM entity_changes WHERE entityName IN ('note_contents', 'note_revision_contents')")
				}
				if (oldVersion < 218 && newVersion >= 218) {
					Log.i(TAG, "migrating to version 218")
					execSQL(
						"""CREATE TABLE IF NOT EXISTS "revisions" (
							revisionId	TEXT NOT NULL PRIMARY KEY,
                            noteId	TEXT NOT NULL,
                            type TEXT DEFAULT '' NOT NULL,
                            mime TEXT DEFAULT '' NOT NULL,
                            title	TEXT NOT NULL,
                            isProtected	INT NOT NULL DEFAULT 0,
                            blobId TEXT DEFAULT NULL,
                            utcDateLastEdited TEXT NOT NULL,
                            utcDateCreated TEXT NOT NULL,
                            utcDateModified TEXT NOT NULL,
                            dateLastEdited TEXT NOT NULL,
                            dateCreated TEXT NOT NULL)"""
					)
					execSQL(
						"INSERT INTO revisions (revisionId, noteId, type, mime, title, isProtected, utcDateLastEdited, utcDateCreated, utcDateModified, dateLastEdited, dateCreated, blobId) "
								+ "SELECT noteRevisionId, noteId, type, mime, title, isProtected, utcDateLastEdited, utcDateCreated, utcDateModified, dateLastEdited, dateCreated, blobId FROM note_revisions"
					)
					execSQL("DROP TABLE note_revisions")
					execSQL("CREATE INDEX IDX_revisions_noteId ON revisions (noteId)")
					execSQL("CREATE INDEX IDX_revisions_utcDateCreated ON revisions (utcDateCreated)")
					execSQL("CREATE INDEX IDX_revisions_utcDateLastEdited ON revisions (utcDateLastEdited)")
					execSQL("CREATE INDEX IDX_revisions_dateCreated ON revisions (dateCreated)")
					execSQL("CREATE INDEX IDX_revisions_dateLastEdited ON revisions (dateLastEdited)")
					execSQL("UPDATE entity_changes SET entityName = 'revisions' WHERE entityName = 'note_revisions'")
				}
				if (oldVersion < 219 && newVersion >= 219) {
					Log.i(TAG, "migrating to version 219")
					execSQL(
						"""CREATE TABLE IF NOT EXISTS "attachments"(
    						attachmentId      TEXT not null primary key,
    						ownerId       TEXT not null,
    						role         TEXT not null,
    						mime         TEXT not null,
    						title         TEXT not null,
    						isProtected    INT  not null DEFAULT 0,
    						position     INT  default 0 not null,
    						blobId    TEXT DEFAULT null,
    						dateModified TEXT NOT NULL,
    						utcDateModified TEXT not null,
    						utcDateScheduledForErasureSince TEXT DEFAULT NULL,
    						isDeleted    INT  not null,
    						deleteId    TEXT DEFAULT NULL)"""
					)
					execSQL("CREATE INDEX IDX_attachments_ownerId_role ON attachments (ownerId, role)")
					execSQL("CREATE INDEX IDX_attachments_utcDateScheduledForErasureSince ON attachments (utcDateScheduledForErasureSince)")
				}
				if (oldVersion < 220 && newVersion >= 220) {
					Log.i(TAG, "migrating to version 220 (no-op currently)")
					// TODO: auto-convert images
				}
				if (oldVersion < 221 && newVersion >= 221) {
					Log.i(TAG, "migrating to version 221")
					execSQL("DELETE FROM options WHERE name = 'hideIncludedImages_main'")
					execSQL("DELETE FROM entity_changes WHERE entityName = 'options' AND entityId = 'hideIncludedImages_main'")
				}
				if (oldVersion < 222 && newVersion >= 222) {
					Log.i(TAG, "migrating to version 222")
					execSQL("UPDATE options SET name = 'openNoteContexts' WHERE name = 'openTabs'")
					execSQL("UPDATE entity_changes SET entityId = 'openNoteContexts' WHERE entityName = 'options' AND entityId = 'openTabs'")
				}
				// 223 is NOOP
				// 224 is a hotfix, already fixed in 216 above
				if (oldVersion < 225 && newVersion >= 225) {
					Log.i(TAG, "migrating to version 225")
					execSQL("CREATE INDEX IF NOT EXISTS IDX_notes_blobId on notes (blobId)")
					execSQL("CREATE INDEX IF NOT EXISTS IDX_revisions_blobId on revisions (blobId)")
					execSQL("CREATE INDEX IF NOT EXISTS IDX_attachments_blobId on attachments (blobId)")
				}
				if (oldVersion < 226 && newVersion >= 226) {
					Log.i(TAG, "migrating to version 226")
					execSQL("UPDATE attributes SET value = 'contentAndAttachmentsAndRevisionsSize' WHERE name = 'orderBy' AND value = 'noteSize'")
				}
				if (oldVersion < 227 && newVersion >= 227) {
					Log.i(TAG, "migrating to version 227")
					execSQL("UPDATE options SET value = 'false' WHERE name = 'compressImages'")
				}
				if (oldVersion < 228 && newVersion >= 228) {
					Log.i(TAG, "migrating to version 228")
					execSQL("UPDATE blobs SET blobId = REPLACE(blobId, '+', 'A')")
					execSQL("UPDATE blobs SET blobId = REPLACE(blobId, '/', 'B')")
					execSQL("UPDATE notes SET blobId = REPLACE(blobId, '+', 'A')")
					execSQL("UPDATE notes SET blobId = REPLACE(blobId, '/', 'B')")
					execSQL("UPDATE attachments SET blobId = REPLACE(blobId, '+', 'A')")
					execSQL("UPDATE attachments SET blobId = REPLACE(blobId, '/', 'B')")
					execSQL("UPDATE revisions SET blobId = REPLACE(blobId, '+', 'A')")
					execSQL("UPDATE revisions SET blobId = REPLACE(blobId, '/', 'B')")
					execSQL("UPDATE entity_changes SET entityId = REPLACE(entityId, '+', 'A') WHERE entityName = 'blobs'")
					execSQL("UPDATE entity_changes SET entityId = REPLACE(entityId, '/', 'B') WHERE entityName = 'blobs';")
				}
				if (oldVersion < 229 && newVersion >= 229) {
					Log.i(TAG, "migrating to version 229")
					execSQL(
						"CREATE TABLE IF NOT EXISTS user_data (tmpID INT, username TEXT, email TEXT, userIDEncryptedDataKey TEXT," +
								"userIDVerificationHash TEXT,salt TEXT,derivedKey TEXT,isSetup TEXT DEFAULT \"false\"," +
								"UNIQUE (tmpID),PRIMARY KEY (tmpID))"
					)
				}
				// https://github.com/TriliumNext/Notes/blob/v0.94.0/apps/server/src/migrations/migrations.ts
				if (oldVersion < 230 && newVersion >= 230) {
					Log.i(TAG, "migrating to version 230")
					execSQL(
						"""
            			CREATE TABLE IF NOT EXISTS "note_embeddings" (
                			"embedId" TEXT NOT NULL PRIMARY KEY,
                			"noteId" TEXT NOT NULL,
                			"providerId" TEXT NOT NULL,
                			"modelId" TEXT NOT NULL,
                			"dimension" INTEGER NOT NULL,
                			"embedding" BLOB NOT NULL,
                			"version" INTEGER NOT NULL DEFAULT 1,
                			"dateCreated" TEXT NOT NULL,
                			"utcDateCreated" TEXT NOT NULL,
                			"dateModified" TEXT NOT NULL,
                			"utcDateModified" TEXT NOT NULL
            			)""".trimIndent()
					)
					execSQL("""CREATE INDEX "IDX_note_embeddings_noteId" ON "note_embeddings" ("noteId")""")
					execSQL("""CREATE INDEX "IDX_note_embeddings_providerId_modelId" ON "note_embeddings" ("providerId", "modelId")""")
					execSQL(
						"""
						CREATE TABLE IF NOT EXISTS "embedding_queue" (
	                		"noteId" TEXT NOT NULL PRIMARY KEY,
			                "operation" TEXT NOT NULL, -- CREATE, UPDATE, DELETE
			                "dateQueued" TEXT NOT NULL,
			                "utcDateQueued" TEXT NOT NULL,
			                "priority" INTEGER NOT NULL DEFAULT 0,
			                "attempts" INTEGER NOT NULL DEFAULT 0,
			                "lastAttempt" TEXT,
			                "error" TEXT,
			                "failed" INTEGER NOT NULL DEFAULT 0,
			                "isProcessing" INTEGER NOT NULL DEFAULT 0
			            )""".trimIndent()
					)
					execSQL(
						"""
						CREATE TABLE IF NOT EXISTS "embedding_providers" (
                			"providerId" TEXT NOT NULL PRIMARY KEY,
                			"name" TEXT NOT NULL,
                			"priority" INTEGER NOT NULL DEFAULT 0,
                			"config" TEXT NOT NULL, -- JSON config object
                			"dateCreated" TEXT NOT NULL,
                			"utcDateCreated" TEXT NOT NULL,
                			"dateModified" TEXT NOT NULL,
                			"utcDateModified" TEXT NOT NULL
            			)""".trimIndent()
					)
				}
				if (oldVersion < 231 && newVersion >= 231) {
					Log.i(TAG, "migrating to version 231")
					execSQL(
						"""
						CREATE TABLE IF NOT EXISTS sessions (
                			id TEXT PRIMARY KEY,
                			data TEXT,
                			expires INTEGER
            			)""".trimIndent()
					)
				}
				// always update to latest version
				execSQL("UPDATE options SET value = '${newVersion}' WHERE name = 'dbVersion'")
			}
		} catch (t: Throwable) {
			Log.e(TAG, "fatal error in database migration", t)
		}
	}

	override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
		// TODO: do something here
	}
}
