package com.goodtvplorer.data

import kotlin.test.Test
import kotlin.test.assertEquals

class SmbConnectionStoreTest {
    @Test
    fun update_replaces_only_the_connection_with_the_same_id() {
        val other = connection("other", "Office")
        val updated = connection("nas", "Living Room 2")
        assertEquals(listOf(other, updated), upsertConnection(listOf(connection("nas", "Living Room"), other), updated))
    }

    @Test
    fun delete_removes_only_the_requested_connection() {
        val first = connection("nas", "Living Room")
        val other = connection("other", "Office")
        assertEquals(listOf(other), removeConnection(listOf(first, other), "nas"))
    }

    private fun connection(id: String, name: String) = SmbConnectionInfo(id, name, "192.168.1.2", share = "media", username = "guest", password = "")
}
