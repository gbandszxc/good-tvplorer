package com.github.gbandszxc.goodtvplorer.data.persistence

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "credential-migration-${UUID.randomUUID()}.db"
    private val alias = "good_tvplorer_migration_test_${UUID.randomUUID()}"
    private val cipher = SmbCredentialCipher(alias)
    private val migration = credentialMigration1To2(cipher)

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test
    fun plaintext_credentials_are_migrated_and_old_bytes_are_removed() {
        val legacyPassword = "legacy-secret-旧密码-8472"
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                """
                INSERT INTO smb_connections
                    (id, name, host, port, share, username, password, domain)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>("nas", "NAS", "192.168.1.2", 445, "media", "user", legacyPassword, null),
            )
            execSQL("INSERT INTO app_preferences (`key`, value) VALUES ('font_scale', '1.15')")
            execSQL(
                "INSERT INTO browser_locations (sourceKey, path, updatedAtMillis) VALUES ('smb:nas', 'Movies', 10)",
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 2, true, migration).apply {
            query("SELECT password FROM smb_connections WHERE id = 'nas'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                val stored = cursor.getString(0)
                assertTrue(stored.startsWith("v1:"))
                assertFalse(stored.contains(legacyPassword))
            }
            query("SELECT value FROM app_preferences WHERE `key` = 'font_scale'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("1.15", cursor.getString(0))
            }
            close()
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(migration)
            .addCallback(SmbCredentialMigrationCleanup)
            .build()
        val restored = runBlocking {
            SmbConnectionRepository(database, cipher).all.first().single()
        }
        assertEquals(legacyPassword, restored.password)
        assertEquals("Movies", runBlocking {
            database.browserNavigationDao().locationFor("smb:nas")
        }?.path)
        database.close()

        val secretBytes = legacyPassword.toByteArray(Charsets.UTF_8)
        databaseFiles(databaseName).forEach { file ->
            assertFalse("${file.name} 不应残留旧明文", file.readBytes().containsSequence(secretBytes))
        }
    }

    private fun databaseFiles(name: String): List<File> {
        val database = context.getDatabasePath(name)
        return listOf(database, File("${database.path}-wal"), File("${database.path}-shm")).filter(File::exists)
    }

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean =
        indices.any { start ->
            start + sequence.size <= size && sequence.indices.all { offset ->
                this[start + offset] == sequence[offset]
            }
        }
}
