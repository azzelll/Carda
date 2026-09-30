package id.carda.auth

import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class IdentityRepository(private val jdbc: JdbcTemplate) {
    private val accountMapper = { rs: java.sql.ResultSet, _: Int ->
        AccountRecord(
            UUID.fromString(rs.getString("id")),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getTimestamp("verified_at")?.toInstant(),
        )
    }

    fun findAccount(email: String): AccountRecord? = jdbc.query(
        "SELECT id, email, password_hash, verified_at FROM accounts WHERE email = ?",
        accountMapper, email,
    ).firstOrNull()

    fun findAccount(id: UUID): AccountRecord? = jdbc.query(
        "SELECT id, email, password_hash, verified_at FROM accounts WHERE id = ?",
        accountMapper, id,
    ).firstOrNull()

    fun createAccount(id: UUID, email: String, passwordHash: String, now: Instant): Boolean =
        jdbc.update(
            "INSERT INTO accounts(id, email, password_hash, created_at) VALUES (?, ?, ?, ?) ON CONFLICT (email) DO NOTHING",
            id, email, passwordHash, java.sql.Timestamp.from(now),
        ) == 1

    fun createActionToken(accountId: UUID, kind: String, hash: String, expires: Instant) {
        jdbc.update("DELETE FROM action_tokens WHERE expires_at < CURRENT_TIMESTAMP")
        jdbc.update("DELETE FROM action_tokens WHERE account_id = ? AND kind = ?", accountId, kind)
        jdbc.update(
            "INSERT INTO action_tokens(id, account_id, kind, token_hash, expires_at) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), accountId, kind, hash, java.sql.Timestamp.from(expires),
        )
    }

    fun consumeActionToken(kind: String, hash: String, now: Instant): UUID? = jdbc.query(
        """UPDATE action_tokens SET consumed_at = ?
           WHERE kind = ? AND token_hash = ? AND consumed_at IS NULL AND expires_at > ?
           RETURNING account_id""".trimIndent(),
        { rs, _ -> UUID.fromString(rs.getString(1)) },
        java.sql.Timestamp.from(now), kind, hash, java.sql.Timestamp.from(now),
    ).firstOrNull()

    fun verifyUnverifiedAccountWithPassword(id: UUID, passwordHash: String, now: Instant): Boolean =
        jdbc.update(
            "UPDATE accounts SET password_hash = ?, verified_at = ? WHERE id = ? AND verified_at IS NULL",
            passwordHash, java.sql.Timestamp.from(now), id,
        ) == 1

    fun replacePassword(id: UUID, hash: String, now: Instant) {
        jdbc.update("UPDATE accounts SET password_hash = ? WHERE id = ?", hash, id)
        jdbc.update("UPDATE sessions SET revoked_at = ? WHERE account_id = ? AND revoked_at IS NULL", java.sql.Timestamp.from(now), id)
    }

    fun createSession(id: UUID, accountId: UUID, tokens: SessionTokens, now: Instant, accessExpiry: Instant, refreshExpiry: Instant) {
        jdbc.update("DELETE FROM sessions WHERE refresh_expires_at < ?", java.sql.Timestamp.from(now))
        jdbc.update(
            """INSERT INTO sessions(id, account_id, access_hash, refresh_hash, access_expires_at,
               refresh_expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
            id, accountId, tokens.access.hash, tokens.refresh.hash,
            java.sql.Timestamp.from(accessExpiry), java.sql.Timestamp.from(refreshExpiry), java.sql.Timestamp.from(now),
        )
    }

    fun accountForAccess(hash: String, now: Instant): UUID? = jdbc.query(
        "SELECT account_id FROM sessions WHERE access_hash = ? AND revoked_at IS NULL AND access_expires_at > ?",
        { rs, _ -> UUID.fromString(rs.getString(1)) }, hash, java.sql.Timestamp.from(now),
    ).firstOrNull()

    fun rotateSession(oldRefreshHash: String, newTokens: SessionTokens, now: Instant, accessExpiry: Instant, refreshExpiry: Instant): UUID? = jdbc.query(
        """UPDATE sessions SET access_hash = ?, refresh_hash = ?, access_expires_at = ?, refresh_expires_at = ?
           WHERE refresh_hash = ? AND revoked_at IS NULL AND refresh_expires_at > ?
           RETURNING account_id""".trimIndent(),
        { rs, _ -> UUID.fromString(rs.getString(1)) },
        newTokens.access.hash, newTokens.refresh.hash, java.sql.Timestamp.from(accessExpiry),
        java.sql.Timestamp.from(refreshExpiry), oldRefreshHash, java.sql.Timestamp.from(now),
    ).firstOrNull()

    fun revokeAccess(accountId: UUID, accessHash: String, now: Instant) {
        jdbc.update(
            "UPDATE sessions SET revoked_at = ? WHERE account_id = ? AND access_hash = ? AND revoked_at IS NULL",
            java.sql.Timestamp.from(now), accountId, accessHash,
        )
    }

    fun deleteAccount(id: UUID) {
        jdbc.update("DELETE FROM accounts WHERE id = ?", id)
    }

    fun recordAttempt(keyHash: String, now: Instant, windowStart: Instant): Int {
        jdbc.update("DELETE FROM rate_limits WHERE window_started_at < ?", java.sql.Timestamp.from(now.minusSeconds(86400)))
        return jdbc.queryForObject(
        """INSERT INTO rate_limits(key_hash, attempts, window_started_at) VALUES (?, 1, ?)
           ON CONFLICT (key_hash) DO UPDATE SET
             attempts = CASE WHEN rate_limits.window_started_at <= ? THEN 1 ELSE rate_limits.attempts + 1 END,
             window_started_at = CASE WHEN rate_limits.window_started_at <= ? THEN EXCLUDED.window_started_at ELSE rate_limits.window_started_at END
           RETURNING attempts""".trimIndent(),
        Int::class.java,
        keyHash, java.sql.Timestamp.from(now), java.sql.Timestamp.from(windowStart), java.sql.Timestamp.from(windowStart),
        ) ?: Int.MAX_VALUE
    }

    fun clearAttempt(keyHash: String) {
        jdbc.update("DELETE FROM rate_limits WHERE key_hash = ?", keyHash)
    }
}
