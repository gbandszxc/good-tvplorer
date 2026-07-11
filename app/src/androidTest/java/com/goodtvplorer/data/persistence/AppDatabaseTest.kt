package com.goodtvplorer.data.persistence

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.goodtvplorer.data.SmbConnectionInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun preferences_and_navigation_records_round_trip() = runBlocking {
        database.appPreferenceDao().put(AppPreferenceEntity("font_scale", "1.15"))
        database.browserNavigationDao().upsertLocation(BrowserLocationEntity("smb:nas", "Shows", 10L))
        database.browserNavigationDao().upsertFocusAnchor(BrowserFocusAnchorEntity("smb:nas", "Shows", "Shows/Season 1", 11L))

        assertEquals("1.15", database.appPreferenceDao().observeValue("font_scale").first())
        assertEquals("Shows", database.browserNavigationDao().locationFor("smb:nas")?.path)
        assertEquals("Shows/Season 1", database.browserNavigationDao().focusAnchorFor("smb:nas", "Shows")?.childPath)
    }

    @Test
    fun deleting_connection_removes_its_navigation_records() = runBlocking {
        val repository = SmbConnectionRepository(database)
        repository.save(SmbConnectionInfo("nas", "NAS", "192.168.1.2", share = "media", username = "guest", password = ""))
        database.browserNavigationDao().upsertLocation(BrowserLocationEntity("smb:nas", "Movies", 10L))
        database.browserNavigationDao().upsertFocusAnchor(BrowserFocusAnchorEntity("smb:nas", "", "Movies", 11L))

        repository.delete("nas")

        assertEquals(emptyList<SmbConnectionEntity>(), database.smbConnectionDao().observeAll().first())
        assertNull(database.browserNavigationDao().locationFor("smb:nas"))
        assertNull(database.browserNavigationDao().focusAnchorFor("smb:nas", ""))
    }
}
