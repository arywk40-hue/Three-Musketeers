package com.eldercareguardian.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider

/**
 * Tests for [DatabaseEncryption].
 *
 * Note: SQLCipher native library is NOT available in the Robolectric test
 * environment, so tests that require the native library verify the
 * correct error-handling behaviour instead.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseEncryptionTest {

    @Test
    fun `isSqlCipherAvailable returns false in test environment`() {
        // SQLCipher native library (.so) is not bundled in Robolectric,
        // so the init block's System.loadLibrary("sqlcipher") will fail.
        // Depending on test ordering the flag may already be false.
        // This test documents the expected CI/test behaviour.
        assertFalse(
            "SQLCipher native library should not be available in unit tests",
            DatabaseEncryption.isSqlCipherAvailable()
        )
    }

    @Test
    fun `supportFactory throws when SQLCipher native library is unavailable`() {
        // Session 16 hardening: supportFactory() now throws instead of
        // silently returning a broken factory when the native lib is missing.
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertThrows(IllegalStateException::class.java) {
            DatabaseEncryption.supportFactory(ctx)
        }
    }
}
