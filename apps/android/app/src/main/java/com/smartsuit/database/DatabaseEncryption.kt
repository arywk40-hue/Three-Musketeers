package com.smartsuit.database

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom

object DatabaseEncryption {

    private const val PREF_FILE = "eldercare_db_encryption"
    private const val KEY_PASSPHRASE = "sqlcipher_passphrase"

    private var _supportFactory: SupportFactory? = null

    fun supportFactory(context: Context): SupportFactory {
        val existing = _supportFactory
        if (existing != null) return existing

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs: SharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        val passphrase = prefs.getString(KEY_PASSPHRASE, null) ?: run {
            val newPass = generatePassphrase()
            prefs.edit().putString(KEY_PASSPHRASE, newPass).apply()
            newPass
        }

        return SupportFactory(passphrase.toByteArray()).also { _supportFactory = it }
    }

    private fun generatePassphrase(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
