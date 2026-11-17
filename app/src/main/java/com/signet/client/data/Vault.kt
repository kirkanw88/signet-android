package com.signet.client.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Entity(tableName = "vault")
data class VaultEntry(@PrimaryKey val id: Int = 1, val encrypted: ByteArray)
@Dao interface VaultDao {
    @Query("SELECT * FROM vault WHERE id=1") suspend fun read(): VaultEntry?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun write(entry: VaultEntry)
}
@Database(entities = [VaultEntry::class], version = 1, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() { abstract fun vault(): VaultDao }

class Vault(context: Context) {
    private val db = Room.databaseBuilder(context, VaultDatabase::class.java, "signet.db").build()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("signet.profiles.v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("signet.profiles.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    suspend fun read(): String? = db.vault().read()?.let {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, it.encrypted.copyOfRange(0, 12)))
        String(cipher.doFinal(it.encrypted.copyOfRange(12, it.encrypted.size)), Charsets.UTF_8)
    }
    suspend fun write(text: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        db.vault().write(VaultEntry(encrypted = cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))))
    }
}
