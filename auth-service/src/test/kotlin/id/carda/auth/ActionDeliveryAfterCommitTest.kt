package id.carda.auth

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.transaction.support.TransactionSynchronizationManager

class ActionDeliveryAfterCommitTest {
    private val mailer = mock(ActionMailer::class.java)

    @AfterEach
    fun clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `verification mail is sent only after token transaction commits`() {
        val delivery = ActionDeliveryAfterCommit(mailer)
        TransactionSynchronizationManager.initSynchronization()

        delivery.verification("synthetic@example.org", "synthetic-code")
        verifyNoInteractions(mailer)
        val callbacks = TransactionSynchronizationManager.getSynchronizations()
        TransactionSynchronizationManager.clearSynchronization()
        callbacks.forEach { it.afterCommit() }

        verify(mailer).sendVerification("synthetic@example.org", "synthetic-code")
    }

    @Test
    fun `rolled back reset token is never mailed`() {
        val delivery = ActionDeliveryAfterCommit(mailer)
        TransactionSynchronizationManager.initSynchronization()

        delivery.passwordReset("synthetic@example.org", "synthetic-code")
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size)
        TransactionSynchronizationManager.clearSynchronization()

        verifyNoInteractions(mailer)
    }

    @Test
    fun `mail is not sent outside a transaction`() {
        val delivery = ActionDeliveryAfterCommit(mailer)
        assertThrows(IllegalStateException::class.java) {
            delivery.verification("synthetic@example.org", "synthetic-code")
        }
        verifyNoInteractions(mailer)
    }

    @Test
    fun `SMTP failure after commit cannot change accepted HTTP outcome`() {
        val failingMailer = mock(ActionMailer::class.java) { invocation ->
            if (invocation.method.name == "sendVerification") throw IllegalStateException("synthetic SMTP outage")
            org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
        val delivery = ActionDeliveryAfterCommit(failingMailer)
        TransactionSynchronizationManager.initSynchronization()
        delivery.verification("synthetic@example.org", "synthetic-code")
        val callbacks = TransactionSynchronizationManager.getSynchronizations()
        TransactionSynchronizationManager.clearSynchronization()

        assertDoesNotThrow { callbacks.forEach { it.afterCommit() } }
        verifyNoInteractions(mailer)
    }
}
