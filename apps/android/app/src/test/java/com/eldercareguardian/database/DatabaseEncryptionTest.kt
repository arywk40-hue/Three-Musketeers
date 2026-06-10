package com.eldercareguardian.database

import androidx.test.core.app.ApplicationProvider
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseEncryptionTest {

    @Test
    fun `supportFactory returns a valid SupportFactory`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val factory = DatabaseEncryption.supportFactory(ctx)
        assertNotNull(factory)
        assertTrue(factory is SupportOpenHelperFactory)
    }

    @Test
    fun `supportFactory caches the same instance across calls`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = DatabaseEncryption.supportFactory(ctx)
        val second = DatabaseEncryption.supportFactory(ctx)
        assertTrue(first === second)
    }
}
