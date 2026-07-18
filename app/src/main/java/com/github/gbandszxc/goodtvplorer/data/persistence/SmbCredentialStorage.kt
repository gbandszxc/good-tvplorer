package com.github.gbandszxc.goodtvplorer.data.persistence

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class SmbCredentialCipher(
    private val keyAlias: String = KEY_ALIAS,
) {
    private val key: SecretKey by lazy(::loadOrCreateKey)

    fun encrypt(connectionId: String, password: String): String = credentialOperation("无法加密 SMB 凭据") {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(connectionId.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv.also { check(it.size == IV_SIZE_BYTES) }
        val ciphertext = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        "$FORMAT_VERSION:${iv.encodeBase64()}:${ciphertext.encodeBase64()}"
    }

    fun decrypt(connectionId: String, encryptedPassword: String): String = credentialOperation("无法解密 SMB 凭据") {
        val parts = encryptedPassword.split(':', limit = 3)
        require(parts.size == 3 && parts[0] == FORMAT_VERSION)
        val iv = parts[1].decodeBase64().also { require(it.size == IV_SIZE_BYTES) }
        val ciphertext = parts[2].decodeBase64().also { require(it.size >= TAG_SIZE_BITS / Byte.SIZE_BITS) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
        cipher.updateAAD(connectionId.toByteArray(Charsets.UTF_8))
        cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun loadOrCreateKey(): SecretKey = synchronized(SmbCredentialCipher::class.java) {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setKeySize(KEY_SIZE_BITS)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
            }
            .generateKey()
    }

    private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private inline fun <T> credentialOperation(message: String, block: () -> T): T = try {
        block()
    } catch (error: Exception) {
        throw IllegalStateException(message, error)
    }

    private companion object {
        const val KEY_ALIAS = "good_tvplorer_smb_credentials_v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = "v1"
        const val KEY_SIZE_BITS = 256
        const val TAG_SIZE_BITS = 128
        const val IV_SIZE_BYTES = 12
    }
}

internal fun credentialMigration1To2(
    cipher: SmbCredentialCipher,
): Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.query("PRAGMA secure_delete = ON").use { cursor ->
            check(cursor.moveToFirst() && cursor.getInt(0) == 1) { "无法启用数据库安全删除" }
        }
        val credentials = buildList {
            db.query("SELECT id, password FROM smb_connections").use { cursor ->
                while (cursor.moveToNext()) {
                    add(cursor.getString(0) to cursor.getString(1))
                }
            }
        }
        db.compileStatement("UPDATE smb_connections SET password = ? WHERE id = ?").use { statement ->
            credentials.forEach { (id, password) ->
                statement.clearBindings()
                statement.bindString(1, cipher.encrypt(id, password))
                statement.bindString(2, id)
                statement.executeUpdateDelete()
            }
        }
        db.compileStatement(
            "INSERT OR REPLACE INTO app_preferences (`key`, value) VALUES (?, '1')",
        ).use { statement ->
            statement.bindString(1, MIGRATION_CLEANUP_KEY)
            statement.executeInsert()
        }
    }
}

internal val MIGRATION_1_2: Migration = credentialMigration1To2(SmbCredentialCipher())

internal object SmbCredentialMigrationCleanup : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        val cleanupPending = db.query(
            "SELECT 1 FROM app_preferences WHERE `key` = '$MIGRATION_CLEANUP_KEY' LIMIT 1",
        ).use { it.moveToFirst() }
        if (!cleanupPending) return

        checkpoint(db, "FULL")
        db.execSQL("VACUUM")
        db.execSQL("DELETE FROM app_preferences WHERE `key` = '$MIGRATION_CLEANUP_KEY'")
        checkpoint(db, "TRUNCATE")
    }

    private fun checkpoint(database: SupportSQLiteDatabase, mode: String) {
        database.query("PRAGMA wal_checkpoint($mode)").use { cursor ->
            check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "无法清理数据库日志" }
        }
    }
}

private const val MIGRATION_CLEANUP_KEY = "__smb_credentials_v2_cleanup_pending"
