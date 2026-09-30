package id.carda.auth

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

@Component
class ActionMailer(
    private val senderProvider: ObjectProvider<JavaMailSender>,
    @Value("\${app.mail-from}") private val from: String,
    @Value("\${spring.mail.host:}") private val host: String,
) {
    fun ensureConfigured() {
        if (senderProvider.ifAvailable == null || from.isBlank() || host.isBlank()) {
            throw ApiFailure(HttpStatus.SERVICE_UNAVAILABLE, "mail_unavailable")
        }
    }

    fun sendVerification(email: String, token: String) = send(
        email, "Verifikasi email Carda",
        "Gunakan kode berikut untuk memverifikasi email Carda Anda: $token\n" +
            "Saat memasukkan kode, pilih kata sandi Anda sendiri. Kode berlaku 24 jam. " +
            "Abaikan jika Anda tidak mendaftar.",
    )

    fun sendReset(email: String, token: String) = send(
        email, "Atur ulang kata sandi Carda",
        "Gunakan kode berikut untuk mengatur ulang kata sandi Carda Anda: $token\nKode berlaku 30 menit. Abaikan jika Anda tidak meminta ini.",
    )

    private fun send(email: String, subject: String, body: String) {
        ensureConfigured()
        val message = SimpleMailMessage()
        message.setFrom(from)
        message.setTo(email)
        message.subject = subject
        message.text = body
        try {
            senderProvider.ifAvailable!!.send(message)
        } catch (_: org.springframework.mail.MailException) {
            throw ApiFailure(HttpStatus.SERVICE_UNAVAILABLE, "mail_unavailable")
        }
    }
}
