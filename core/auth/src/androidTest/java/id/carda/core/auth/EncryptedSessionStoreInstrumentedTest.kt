package id.carda.core.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EncryptedSessionStoreInstrumentedTest {
    @Test fun sessionCiphertextIsLocalAndClears() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val store = EncryptedSessionStore(context)
        store.clear()
        val session = AuthSession("ec87a9d1-6035-443b-b14a-ad36d879c9d8",
            "fake-access-test-only", "fake-refresh-test-only", 1_790_000_000_000)
        store.write(session)
        assertEquals(session, store.read())
        val file = File(context.noBackupFilesDir, "carda_auth_session.bin")
        assertTrue(file.exists())
        assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("fake-access-test-only"))
        store.clear()
        assertNull(store.read())
    }
}
