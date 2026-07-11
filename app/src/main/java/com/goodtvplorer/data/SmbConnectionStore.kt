package com.goodtvplorer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

private val Context.smbDataStore by preferencesDataStore("smb_connections")

class SmbConnectionStore(private val context: Context) {
    private val connectionsKey = stringSetPreferencesKey("connections")

    val connections: Flow<List<SmbConnectionInfo>> = context.smbDataStore.data.map { prefs ->
        prefs[connectionsKey].orEmpty().mapNotNull(::decode).sortedBy { it.name.lowercase() }
    }

    suspend fun add(info: SmbConnectionInfo) {
        val fixed = info.copy(id = info.id.ifBlank { UUID.randomUUID().toString() })
        context.smbDataStore.edit { prefs ->
            val connections = prefs[connectionsKey].orEmpty().mapNotNull(::decode)
            prefs[connectionsKey] = upsertConnection(connections, fixed).map(::encode).toSet()
        }
    }

    suspend fun delete(id: String) {
        context.smbDataStore.edit { prefs ->
            val connections = prefs[connectionsKey].orEmpty().mapNotNull(::decode)
            prefs[connectionsKey] = removeConnection(connections, id).map(::encode).toSet()
        }
    }

    private fun encode(info: SmbConnectionInfo): String {
        // TODO: 密码当前为 MVP 明文保存，后续改为 EncryptedSharedPreferences 或 Android Keystore。
        return listOf(info.id, info.name, info.host, info.port.toString(), info.share, info.username, info.password, info.domain.orEmpty())
            .joinToString("|") { URLEncoder.encode(it, StandardCharsets.UTF_8.name()) }
    }

    private fun decode(raw: String): SmbConnectionInfo? {
        val parts = raw.split("|").map { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
        if (parts.size != 8) return null
        return SmbConnectionInfo(parts[0], parts[1], parts[2], parts[3].toIntOrNull() ?: 445, parts[4], parts[5], parts[6], parts[7].ifBlank { null })
    }
}

internal fun upsertConnection(connections: List<SmbConnectionInfo>, connection: SmbConnectionInfo): List<SmbConnectionInfo> =
    connections.filterNot { it.id == connection.id } + connection

internal fun removeConnection(connections: List<SmbConnectionInfo>, id: String): List<SmbConnectionInfo> =
    connections.filterNot { it.id == id }
