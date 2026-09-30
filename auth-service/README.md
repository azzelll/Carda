# Carda identity service

This Kotlin/Spring Boot service handles **identity and sessions only**. It has no route or table for health profile data, PPG, frames, measurements, or history. The Android client stores those locally by account ID.

## API v1

All routes use JSON and live below `/api/v1/auth`.

| Method and route | Request | Success | Notes |
| --- | --- | --- | --- |
| `POST /register` | `email`, `password` | `202 {"status":"accepted"}` | Creates a 24-hour email code. Existing emails and a configured relay's temporary delivery failure have the same public response. Any unverified address can request a fresh code, which invalidates its prior code. The registration password is provisional; it cannot authenticate until code redemption. This response alone does not prove mail delivery. |
| `POST /verify-email` | `token`, `newPassword` (12–128 characters) | `204` | The email recipient redeems the latest single-use code and chooses the account password. The password and verified status change together only while the account is unverified. An old code cannot reset a verified account. |
| `POST /login` | `email`, `password` | `200` session JSON | Generic `invalid_credentials` on wrong password or unverified account. |
| `POST /refresh` | `refreshToken` | `200` rotated session JSON | Previous access and refresh tokens are invalidated. |
| `POST /logout` | Empty body | `204` | Requires `Authorization: Bearer <accessToken>`. Revokes this session. |
| `POST /password-reset/request` | `email` | `202 {"status":"accepted"}` | Sends a 30-minute code for verified accounts, with identical public response for unknown emails. |
| `POST /password-reset/confirm` | `token`, `newPassword` | `204` | Single-use code; revokes all existing sessions. |
| `DELETE /account` | `password` | `204` | Requires bearer access token and password. Cascades server session/code deletion. Android must separately delete its local account data. |

Session JSON is `{ "accountId": "UUID", "accessToken": "opaque", "refreshToken": "opaque", "expiresInSeconds": 900 }`. Access tokens expire after 15 minutes; refresh tokens after 30 days. The database stores SHA-256 token hashes, never plaintext tokens. Passwords use Argon2id. All code and session operations are transactional; a refreshed token pair replaces its predecessor atomically. Rate-limit state uses HMAC keys with `RATE_LIMIT_PEPPER`. The service never logs credentials or request bodies. A deployment should also add edge-level abuse controls; application limits alone are insufficient against distributed traffic.

## Account-deletion portal

The service also serves `/account-delete/index.html`, with local `portal.js` and `portal.css`. This is a minimal Indonesian **identity-only** browser flow: sign in, enter the password again, check explicit confirmation, then call the existing authenticated `DELETE /api/v1/auth/account` endpoint. Cancel attempts to revoke the portal session. The page distinguishes server identity/session deletion from health profile, results and history stored on each phone; people must erase those local records separately after deleting through the browser. Deletion within the Android app has its own local cleanup flow.

The portal keeps the access token in page memory only, clears it on page exit, uses same-origin JSON requests with no cookies/local storage/telemetry, and rejects use over ordinary HTTP except loopback development hosts. Its server response has a restrictive Content Security Policy and no-referrer header. A network interruption after sending a delete request has an **unknown** outcome, so the page asks the user to sign in again rather than claiming success. This route is only local source and test evidence until the team supplies a public HTTPS deployment and approved policy/listing URL.

## Build and local run

Requires JDK 17+, Maven 3.6.3+, PostgreSQL, and a working SMTP relay. Build with `mvn test package`. Flyway applies the schema on startup and Hibernate does not generate tables.

Required environment variables:

| Variable | Purpose |
| --- | --- |
| `DATABASE_URL` | PostgreSQL JDBC URL, e.g. `jdbc:postgresql://localhost:5432/carda_auth` |
| `DATABASE_USER`, `DATABASE_PASSWORD` | Database login with migration rights |
| `RATE_LIMIT_PEPPER` | Random secret of at least 32 bytes, supplied by a secret manager |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD` | SMTP relay; STARTTLS is enabled **and required** by default |
| `MAIL_FROM` | Verified sender email address |
| `PORT` | HTTP port, default 8080 |

No default database password, token secret, or SMTP credential is committed. With no SMTP host or sender, registration and password reset return `503 mail_unavailable` and create no usable account/token. If an already configured relay fails during registration, the public response remains `202 accepted`; the account remains unverified and a later registration attempt with any provisional password can request a new code. The owner of that email chooses the final password only when redeeming the code. If that relay fails during a reset request, the public response likewise remains `202 accepted` for both known and unknown addresses; delivery failure is logged without the address and the unusable code expires. SMTP sending occurs **after the token transaction commits but before the HTTP request finishes**, with 5-second connection/read/write timeouts. This avoids mailing a rolled-back code and does not rely on background CPU in Cloud Run. A slow relay can delay the response and expose account-state timing differences. There is **no durable outbox or automatic delivery retry**; the team must design and test that reliability path before production use. The endpoint `/actuator/health` is public; no other actuator endpoint is exposed. Its mail indicator is disabled so a temporary relay outage does not remove login availability. Monitor SMTP delivery separately. Error responses are machine-readable codes without exception details. Disposable loopback SMTP tests explicitly override both `spring.mail.properties.mail.smtp.starttls.enable=false` and `spring.mail.properties.mail.smtp.starttls.required=false`; this override is **never** a production configuration.

For USB phone tests with synthetic accounts, [`scripts/local_test_smtp.py`](scripts/local_test_smtp.py) provides a loopback-only disposable SMTP sink. It writes verification/reset codes to a private file outside the repository; delete the file after testing. The build, temporary plaintext-mail override and `adb reverse` sequence are documented in [`team-testing-handoff.md`](../docs/engineering/team-testing-handoff.md). Never connect this sink to a non-local network or use real participant email addresses with it.

To build a container, first run `mvn package`, then `docker build -t carda-auth .`. The image listens on `$PORT` and runs as an unprivileged user. Do not put environment secrets in the image or deployment manifest.

## Cloud Run / Cloud SQL handoff

Provision a dedicated Cloud SQL PostgreSQL database and service account. Use Cloud Run HTTPS ingress plus a Cloud SQL Auth Proxy sidecar or equivalent authenticated connection path; point `DATABASE_URL` at the proxy's local PostgreSQL port. Supply database credentials, rate-limit pepper, and SMTP secrets from Secret Manager. Restrict the database account and network, configure Cloud Armor or equivalent edge rate limiting, and verify SMTP egress. Do not deploy this service before the team supplies the GCP project, billing, domain, sender, privacy policy, and operational owner.

The Android client must use the deployed HTTPS base URL, protect tokens using Android Keystore, exclude them and local health data from backup, and continue local measurement/history offline after a successful login. Logging out invalidates the remote session; deleting the account requires a separate confirmed local-data deletion flow. Registration and reset are unavailable offline. No server-side health-data sync exists.

## Evidence gaps

The current **clean** Maven gate has **25 passing tests**, including after-commit/rollback delivery checks ([evidence E-057](../docs/engineering/evidence-index.md)). A disposable PostgreSQL/fake-SMTP ordinary and adversarial pre-registration smoke matched **21 expected HTTP statuses**; a configured-relay outage/retry fixture matched another **12 statuses** (E-058). The earlier browser run checked seven portal flow/edge cases at desktop/mobile widths on the prior backend source (E-054); rerun it before a candidate claim. One Android emulator used the revised contract against a local backend, then reopened the local session/history offline (E-056). These are local synthetic/integration checks, not production mail delivery, transport security, timing-attack measurement or cloud deployment. A deployed pass still needs real Cloud SQL migration/transactions, real SMTP delivery and reliability, rate-limit behavior behind Cloud Run ingress, TLS, secret rotation, the public deletion URL, and a second-device/account-deletion audit. These are required before calling account access production ready.

For a **disposable local** HTTP check with a local PostgreSQL database and fake SMTP capture, set `CARDA_SMOKE_BASE_URL` and `CARDA_SMOKE_MAIL_FILE`, then run `python3 scripts/local_smoke.py`. It creates unique fake accounts, exercises verification, login, refresh rotation, password reset, deletion and an adversarial pre-registration scenario where the emailed code recipient chooses the final password. The fake SMTP file contains short-lived test codes and must be removed after the run. This script is not an end-to-end Cloud Run/real-email check.
