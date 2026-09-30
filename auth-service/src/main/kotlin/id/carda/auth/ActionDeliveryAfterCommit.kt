package id.carda.auth

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/** Sends during the request after a token commits, so request-scoped Cloud Run CPU suffices. */
@Component
class ActionDeliveryAfterCommit(
    private val mailer: ActionMailer,
) {
    private val logger = LoggerFactory.getLogger(ActionDeliveryAfterCommit::class.java)

    fun verification(email: String, token: String) = afterCommit {
        mailer.sendVerification(email, token)
    }

    fun passwordReset(email: String, token: String) = afterCommit {
        mailer.sendReset(email, token)
    }

    private fun afterCommit(deliver: () -> Unit) {
        check(TransactionSynchronizationManager.isSynchronizationActive()) {
            "Action email requires an active transaction"
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                try {
                    deliver()
                } catch (_: Exception) {
                    // A later user-requested code can be issued; do not leak account existence or codes.
                    logger.warn("Action email delivery failed")
                }
            }
        })
    }
}
