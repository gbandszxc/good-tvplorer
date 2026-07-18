package com.github.gbandszxc.goodtvplorer.data.persistence

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmbCredentialCipherTest {
    private val alias = "good_tvplorer_test_${UUID.randomUUID()}"
    private val cipher = SmbCredentialCipher(alias)

    @After
    fun tearDown() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test
    fun passwords_round_trip_with_random_ciphertext() {
        listOf("", "密碼-Secret-123").forEach { password ->
            val first = cipher.encrypt("nas", password)
            val second = cipher.encrypt("nas", password)

            assertNotEquals(first, second)
            assertEquals(password, cipher.decrypt("nas", first))
            assertEquals(password, cipher.decrypt("nas", second))
        }
    }

    @Test
    fun tampered_ciphertext_and_wrong_connection_id_are_rejected() {
        val encrypted = cipher.encrypt("nas", "secret")
        val parts = encrypted.split(':')
        val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP).also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }
        val tampered = "${parts[0]}:${parts[1]}:${Base64.encodeToString(ciphertext, Base64.NO_WRAP)}"

        expectCredentialFailure { cipher.decrypt("nas", tampered) }
        expectCredentialFailure { cipher.decrypt("other-nas", encrypted) }
        expectCredentialFailure { cipher.decrypt("nas", "plaintext") }
    }

    private fun expectCredentialFailure(block: () -> Unit) {
        try {
            block()
            fail("密文无效时应拒绝解密")
        } catch (error: IllegalStateException) {
            assertEquals("无法解密 SMB 凭据", error.message)
        }
    }
}
