package id.carda.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSaveTransactionTest {
    @Test fun cancelledFolderPickerDoesNotCreateOrDeleteAnything() = runBlocking {
        var created = false
        var deleted = false
        val outcome = savePdfInSelectedFolder<String, String, String>(
            folder = null,
            payload = "accepted summary",
            permitted = { true },
            create = { _, _ -> created = true; "new.pdf" },
            write = { _, _ -> error("should not write") },
            delete = { deleted = true; true },
        )
        assertEquals(PdfSaveOutcome.Cancelled, outcome)
        assertFalse(created)
        assertFalse(deleted)
    }

    @Test fun staleAccountBeforeCreationLeavesExistingFilesUntouched() = runBlocking {
        val files = mutableSetOf("existing.pdf")
        val outcome = savePdfInSelectedFolder(
            folder = "chosen folder",
            payload = "accepted summary",
            permitted = { false },
            create = { _, _ -> files.add("new.pdf"); "new.pdf" },
            write = { _: String, _: String -> error("should not write") },
            delete = { files.remove(it) },
        )
        assertEquals(PdfSaveOutcome.StaleBeforeCreate, outcome)
        assertEquals(setOf("existing.pdf"), files)
    }

    @Test fun accountChangeAfterCreationDeletesOnlyTheNewDocument() = runBlocking {
        val files = mutableSetOf("existing.pdf")
        var allowed = true
        val outcome = savePdfInSelectedFolder(
            folder = "chosen folder",
            payload = "accepted summary",
            permitted = { allowed },
            create = { _, _ -> files.add("new.pdf"); allowed = false; "new.pdf" },
            write = { _: String, _: String -> error("should not write") },
            delete = { files.remove(it) },
        )
        assertEquals(PdfSaveOutcome.StaleAfterCreate(deleted = true), outcome)
        assertEquals(setOf("existing.pdf"), files)
    }

    @Test fun providerRefusingDeletionReportsManualCleanup() = runBlocking {
        var allowed = true
        val outcome = savePdfInSelectedFolder(
            folder = "chosen folder",
            payload = "accepted summary",
            permitted = { allowed },
            create = { _, _ -> allowed = false; "new.pdf" },
            write = { _: String, _: String -> error("should not write") },
            delete = { false },
        )
        assertEquals(PdfSaveOutcome.StaleAfterCreate(deleted = false), outcome)
        assertTrue(outcome.userMessage().contains("hapus", ignoreCase = true))
        assertTrue(outcome.userMessage().contains("mungkin", ignoreCase = true))
        assertFalse(outcome.userMessage().contains("berhasil disimpan", ignoreCase = true))
    }

    @Test fun providerThrowingOnDeletionAlsoReportsManualCleanup() = runBlocking {
        var allowed = true
        val outcome = savePdfInSelectedFolder(
            folder = "chosen folder",
            payload = "accepted summary",
            permitted = { allowed },
            create = { _, _ -> allowed = false; "new.pdf" },
            write = { _: String, _: String -> error("should not write") },
            delete = { throw SecurityException("provider denied deletion") },
        )
        assertEquals(PdfSaveOutcome.StaleAfterCreate(deleted = false), outcome)
        assertTrue(outcome.userMessage().contains("hapus", ignoreCase = true))
        assertTrue(outcome.userMessage().contains("mungkin", ignoreCase = true))
    }

    @Test fun accountChangeDuringWriteDeletesOnlyTheNewDocument() = runBlocking {
        val files = mutableSetOf("existing.pdf")
        var allowed = true
        val outcome = savePdfInSelectedFolder(
            folder = "chosen folder",
            payload = "accepted summary",
            permitted = { allowed },
            create = { _, _ -> files.add("new.pdf"); "new.pdf" },
            write = { _: String, _: String -> allowed = false },
            delete = { files.remove(it) },
        )
        assertEquals(PdfSaveOutcome.StaleAfterCreate(deleted = true), outcome)
        assertEquals(setOf("existing.pdf"), files)
    }

    @Test fun writeFailureAttemptsCleanupOfOnlyCreatedDocument() = runBlocking {
        val files = mutableSetOf("existing.pdf")
        val outcome = savePdfInSelectedFolder(
            folder = "chosen folder",
            payload = "accepted summary",
            permitted = { true },
            create = { _, _ -> files.add("new.pdf"); "new.pdf" },
            write = { _: String, _: String -> error("storage failure") },
            delete = { files.remove(it) },
        )
        assertEquals(PdfSaveOutcome.WriteFailed(deleted = true), outcome)
        assertEquals(setOf("existing.pdf"), files)
    }
}
