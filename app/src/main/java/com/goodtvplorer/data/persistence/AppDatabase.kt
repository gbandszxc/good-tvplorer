package com.goodtvplorer.data.persistence

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.withTransaction
import com.goodtvplorer.data.SmbConnectionInfo
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "smb_connections")
data class SmbConnectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val share: String,
    val username: String,
    val password: String,
    val domain: String?,
)

@Entity(tableName = "app_preferences")
data class AppPreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "browser_locations")
data class BrowserLocationEntity(
    @PrimaryKey val sourceKey: String,
    val path: String,
    val updatedAtMillis: Long,
)

@Entity(tableName = "browser_focus_anchors", primaryKeys = ["sourceKey", "parentPath"])
data class BrowserFocusAnchorEntity(
    val sourceKey: String,
    val parentPath: String,
    val childPath: String,
    val updatedAtMillis: Long,
)

@Dao
interface SmbConnectionDao {
    @Query("SELECT * FROM smb_connections ORDER BY lower(name), id")
    fun observeAll(): Flow<List<SmbConnectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(connection: SmbConnectionEntity)

    @Query("DELETE FROM smb_connections WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface AppPreferenceDao {
    @Query("SELECT value FROM app_preferences WHERE key = :key LIMIT 1")
    fun observeValue(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(preference: AppPreferenceEntity)
}

@Dao
interface BrowserNavigationDao {
    @Query("SELECT * FROM browser_locations")
    suspend fun locations(): List<BrowserLocationEntity>

    @Query("SELECT * FROM browser_focus_anchors")
    suspend fun focusAnchors(): List<BrowserFocusAnchorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocation(location: BrowserLocationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFocusAnchor(anchor: BrowserFocusAnchorEntity)

    @Query("DELETE FROM browser_locations WHERE sourceKey = :sourceKey")
    suspend fun deleteLocationsForSource(sourceKey: String)

    @Query("DELETE FROM browser_focus_anchors WHERE sourceKey = :sourceKey")
    suspend fun deleteFocusAnchorsForSource(sourceKey: String)
}

@Database(
    entities = [SmbConnectionEntity::class, AppPreferenceEntity::class, BrowserLocationEntity::class, BrowserFocusAnchorEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smbConnectionDao(): SmbConnectionDao
    abstract fun appPreferenceDao(): AppPreferenceDao
    abstract fun browserNavigationDao(): BrowserNavigationDao
}

object AppDatabaseProvider {
    @Volatile private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "good_tvplorer.db")
            .build()
            .also { instance = it }
    }
}

class SmbConnectionRepository(context: Context) {
    private val database = AppDatabaseProvider.get(context)
    private val connections = database.smbConnectionDao()
    private val navigation = database.browserNavigationDao()

    val all: Flow<List<SmbConnectionInfo>> = connections.observeAll().map { items -> items.map(SmbConnectionEntity::toModel) }

    suspend fun save(info: SmbConnectionInfo) {
        val fixed = info.copy(id = info.id.ifBlank { UUID.randomUUID().toString() })
        connections.upsert(fixed.toEntity())
    }

    suspend fun delete(id: String) {
        database.withTransaction {
            connections.deleteById(id)
            navigation.deleteLocationsForSource("smb:$id")
            navigation.deleteFocusAnchorsForSource("smb:$id")
        }
    }
}

class DisplaySettingsRepository(context: Context) {
    private val preferences = AppDatabaseProvider.get(context).appPreferenceDao()

    val fontScale: Flow<Float> = preferences.observeValue(FONT_SCALE_KEY).map { value ->
        value?.toFloatOrNull()?.coerceIn(0.8f, 1.2f) ?: 1f
    }

    suspend fun setFontScale(value: Float) {
        preferences.put(AppPreferenceEntity(FONT_SCALE_KEY, value.coerceIn(0.8f, 1.2f).toString()))
    }

    private companion object {
        const val FONT_SCALE_KEY = "font_scale"
    }
}

data class BrowserNavigationSnapshot(
    val locations: List<BrowserLocationEntity>,
    val focusAnchors: List<BrowserFocusAnchorEntity>,
)

class BrowserNavigationRepository(context: Context) {
    private val navigation = AppDatabaseProvider.get(context).browserNavigationDao()

    suspend fun restore(): BrowserNavigationSnapshot = BrowserNavigationSnapshot(
        locations = navigation.locations(),
        focusAnchors = navigation.focusAnchors(),
    )

    suspend fun recordLocation(sourceKey: String, path: String, updatedAtMillis: Long) {
        navigation.upsertLocation(BrowserLocationEntity(sourceKey, path, updatedAtMillis))
    }

    suspend fun recordFocusAnchor(sourceKey: String, parentPath: String, childPath: String, updatedAtMillis: Long) {
        navigation.upsertFocusAnchor(BrowserFocusAnchorEntity(sourceKey, parentPath, childPath, updatedAtMillis))
    }
}

private fun SmbConnectionEntity.toModel(): SmbConnectionInfo = SmbConnectionInfo(
    id = id,
    name = name,
    host = host,
    port = port,
    share = share,
    username = username,
    password = password,
    domain = domain,
)

private fun SmbConnectionInfo.toEntity(): SmbConnectionEntity = SmbConnectionEntity(
    id = id,
    name = name,
    host = host,
    port = port,
    share = share,
    username = username,
    password = password,
    domain = domain,
)
