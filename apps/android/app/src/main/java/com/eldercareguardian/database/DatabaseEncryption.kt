package com.eldercareguardian.database

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.KeyStoreException
import java.security.SecureRandom
import kotlin.jvm.Volatile

/**
 * Manages SQLCipher database encryption passphrase.
 *
 * Security hardening (Session 16):
 *  - SQLCipher native library load failure is now tracked and surfaced
 *    instead of silently swallowed.
 *  - KeyStore failure fallback now persists the passphrase in regular
 *    SharedPreferences (less secure but keeps the DB readable across
 *    process restarts). A public flag [isUsingFallbackEncryption] lets
 *    the UI show a warning banner.
 */
object DatabaseEncryption {

    private const val TAG = "DatabaseEncryption"
    private const val PREF_FILE = "eldercare_db_encryption"
    private const val FALLBACK_PREF_FILE = "eldercare_db_fallback"
    private const val KEY_PASSPHRASE = "sqlcipher_passphrase"

    /** True if the SQLCipher native library loaded successfully. */
    var sqlCipherAvailable: Boolean = true
        private set

    /**
     * True if the passphrase is stored in regular (unencrypted) SharedPreferences
     * because AndroidKeyStore was unavailable. The UI should show a degraded-security
     * warning when this is set.
     */
    var isUsingFallbackEncryption: Boolean = false
        private set

    private var sqlCipherLoadError: Throwable? = null

    init {
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: UnsatisfiedLinkError) {
            sqlCipherAvailable = false
            sqlCipherLoadError = e
            Log.e(TAG, "SQLCipher native library failed to load", e)
        }
    }

    /** Check whether the SQLCipher native library is available. */
    fun isSqlCipherAvailable(): Boolean = sqlCipherAvailable

    @Volatile
    private var _supportFactory: SupportOpenHelperFactory? = null

    /**
     * Returns the [SupportOpenHelperFactory] for Room, creating the passphrase
     * on first call and caching it.
     *
     * @throws IllegalStateException if the SQLCipher native library is not loaded.
     */
    fun supportFactory(context: Context): SupportOpenHelperFactory {
        // Fast path — already initialized (volatile read, no lock needed)
        _supportFactory?.let { return it }

        // Slow path — synchronized init with proper double-check
        return synchronized(this) {
            _supportFactory ?: run {
                if (!sqlCipherAvailable) {
                    throw IllegalStateException(
                        "SQLCipher native library failed to load. " +
                        "Database encryption is unavailable. Error: ${sqlCipherLoadError?.message}"
                    )
                }
                val passphrase = try {
                    getOrCreateEncryptedPassphrase(context)
                } catch (e: KeyStoreException) {
                    Log.w(TAG, "AndroidKeyStore unavailable, using fallback storage", e)
                    getOrCreateFallbackPassphrase(context)
                } catch (e: java.security.NoSuchAlgorithmException) {
                    Log.w(TAG, "KeyStore algorithm unavailable, using fallback storage", e)
                    getOrCreateFallbackPassphrase(context)
                } catch (e: Exception) {
                    // Catch-all for EncryptedSharedPreferences init failures
                    // (e.g. corrupted tink keyset, device migration edge cases)
                    Log.w(TAG, "EncryptedSharedPreferences failed, using fallback storage", e)
                    getOrCreateFallbackPassphrase(context)
                }
                SupportOpenHelperFactory(passphrase.toByteArray()).also { _supportFactory = it }
            }
        }
    }

    /**
     * Primary path: store passphrase in EncryptedSharedPreferences backed by
     * AndroidKeyStore AES-256-GCM.
     */
    private fun getOrCreateEncryptedPassphrase(context: Context): String {
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

        return prefs.getString(KEY_PASSPHRASE, null) ?: run {
            val newPass = generatePassphrase()
            prefs.edit().putString(KEY_PASSPHRASE, newPass).apply()
            newPass
        }
    }

    /**
     * Fallback path: store passphrase in regular SharedPreferences.
     * Less secure but persistent across process restarts — prevents the
     * silent data-loss bug where a fresh passphrase was generated each time,
     * making the previous encrypted database permanently unreadable.
     */
    private fun getOrCreateFallbackPassphrase(context: Context): String {
        isUsingFallbackEncryption = true
        val prefs = context.getSharedPreferences(FALLBACK_PREF_FILE, Context.MODE_PRIVATE)

        return prefs.getString(KEY_PASSPHRASE, null) ?: run {
            val newPass = generatePassphrase()
            prefs.edit().putString(KEY_PASSPHRASE, newPass).apply()
            Log.w(TAG, "Generated new fallback passphrase — DB encrypted with unprotected key")
            newPass
        }
    }

    private fun generatePassphrase(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
