# Carda team testing handoff

Prepared 30 September 2026 for a teammate using a physical Android phone and an AI assistant. The Android repository and historical engineering summaries are in Git; **APK/AAB files, `/private/tmp` command logs, test mail, and participant data are intentionally not in Git**. A fresh clone must build its own APK and record its own SHA-256. The latest local software/setup rerun is E-20260930-011; no physical phone, reference-instrument or participant validation has been recorded in [`evidence-index.md`](evidence-index.md).

The copy-ready AI instructions are in [`team-testing-ai-prompt.md`](team-testing-ai-prompt.md).

## Read in order

1. [`AGENTS.md`](../../AGENTS.md), [`architecture.md`](../architecture.md), [`ppg-quality-and-safety.md`](../ppg-quality-and-safety.md): boundaries, local data and safety rules.
2. [`requirements-matrix.md`](requirements-matrix.md) and [`progress.md`](progress.md): F01–F20 requirements and historical progress. The matrix's I/T/V fields are open; a software pass is not physiological validity.
3. [`priority-validation-handoff.md`](priority-validation-handoff.md) and [`device-matrix.md`](../device-matrix.md): F02–F05/F09, plus debug-only F11/F12 research observations.
4. [`support-feature-test-handoff.md`](support-feature-test-handoff.md): F06/F07/F08/F17/F18 and conditional F20 tests.
5. [`validation-plan.md`](validation-plan.md), [`decisions-and-blockers.md`](decisions-and-blockers.md), [`release-readiness.md`](release-readiness.md): reference methods, external needs and release gates.

## Build and connect

Use JDK 17, Android SDK 36, a USB cable, and an Android API 26+ phone with a rear camera and torch. Enable developer options/USB debugging and authorize the computer on the phone. Android Studio may install the SDK; point `ANDROID_HOME` or `local.properties` at the actual SDK path. From the repository root:

```bash
git rev-parse HEAD
./gradlew :app:assembleDebug
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
shasum -a 256 app/build/outputs/apk/debug/app-debug.apk
```

This **generic debug** build allows a clearly labelled anonymous engineering capture without an account. It does **not** save that anonymous result, so it cannot prove history/dashboard/reminder flows. If `shasum` is unavailable, use `sha256sum`. The build can change its hash on a different environment; never substitute a historical hash from the evidence index for the file actually installed.

For account-scoped tests, provision a **disposable local PostgreSQL database** and run the identity service from [`auth-service/README.md`](../../auth-service/README.md). A loopback-only SMTP sink is provided for synthetic accounts:

```bash
python3 auth-service/scripts/local_test_smtp.py --port 25252 --output /tmp/carda-test-mail.log
```

In another terminal, set `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `RATE_LIMIT_PEPPER` (at least 32 random bytes), `MAIL_FROM`, `SMTP_HOST=127.0.0.1`, `SMTP_PORT=25252`, `SMTP_AUTH=false`, and `PORT=8080`. Keep secrets out of shell history, Git and shared reports. Run the service from `auth-service/` with JDK 17 and Maven:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments='--spring.mail.properties.mail.smtp.starttls.enable=false --spring.mail.properties.mail.smtp.starttls.required=false'
```

The two plaintext-mail overrides are **local-test-only**; never use them for a networked or production server. Confirm `http://127.0.0.1:8080/actuator/health` returns an available service. Read the one-time code from `/tmp/carda-test-mail.log` locally, then remove that file after the test. Use only synthetic email addresses. If the test service is unavailable, mark F01/F06/F08/F17/F18 account paths **blocked**; do not use anonymous capture as evidence for persistence.

Build the **account-capable debug** APK and connect the phone's loopback port to the laptop:

```bash
./gradlew :app:assembleDebug -PcardaAuthBaseUrl=http://127.0.0.1:8080
adb reverse tcp:8080 tcp:8080
adb install -r app/build/outputs/apk/debug/app-debug.apk
shasum -a 256 app/build/outputs/apk/debug/app-debug.apk
```

Register, redeem the synthetic email code with a recipient-chosen password, and log in. `adb reverse` is a USB debug bridge; this APK is **not** an HTTPS release/privacy candidate. For offline-after-login, remove the reverse mapping, disable phone connectivity, force-stop/reopen and inspect local features. Reconnect the reverse mapping for later server operations. Reboot can clear the mapping. Do not share this APK or test-mail contents.

## Testing order and boundaries

| Order | Features | Physical observation to record |
| --- | --- | --- |
| 1 | F02–F04 | Device preflight, rear torch/stream/cadence, actual trace, accepted/rejected quality, retry, cancel/background/timeout and torch release. No numerical result after rejection. |
| 2 | F05/F09 | HR and PRV availability on accepted windows; compare with synchronized ECG/beat intervals only under a frozen protocol. A displayed number alone is not accuracy evidence. |
| 3 | F11/F12 | Debug-only, transient research candidate after valid capture. F11 is a **unitless optical feature, not SpO₂ %**. F12 requires the longer window. Record withheld candidates too; no production-value claim. |
| 4 | F01/F06/F08/F15/F16/F17/F18 | With a disposable logged-in account: consent, local result/restart/trend, offline isolation/privacy, profile, SAF PDF, reminder delivery/cancel, dashboard and unavailable states. Follow the support guide. |
| Later | F07/F19/F20 | TalkBack/large text and human comprehension; landing browser; signed release and Play track only after the listed external inputs exist. |

F10 ECG Insight, F13 blood-pressure numbers, and F14 risk categories are **not ready for final user-output validation**; the unavailable states and safety wording can be checked now. They remain F01–F20 requirements. Do not invent a model, calibration, clinical threshold, diagnosis or successful Play submission to mark them complete.

## Report back to engineering

For each attempt, record: `feature/criterion`, source commit, APK hash, anonymous device ID/model/API, app/pipeline/model version, date/time and conditions, exact steps, expected, observed, pass/fail/blocked, SQI and rejection reason where relevant, reference instrument/time alignment if used, and aggregate-safe evidence location. Count **people**, attempts, accepted and rejected attempts separately. Add a row to [`device-matrix.md`](../device-matrix.md) for a tested phone and a new row to [`evidence-index.md`](evidence-index.md) only after a real observation. Mark any claim `requires-rerun` after changing camera, threshold, pipeline, model or relevant UI.

Stop the session and flag a release blocker for a number after failed SQI, torch/camera left on after exit, a crash, health data sent to the identity server or log, or a cross-account data leak. Do not commit or share frames, raw PPG/ECG, identifiable participant details, real credentials/tokens, test-mail files, APKs or reference datasets. A screenshot may be blocked by `FLAG_SECURE`; write an anonymous observation rather than bypassing privacy protection. Engineering will reproduce software defects and update tests; the testing teammate should report the finding and exact build before changing code.
