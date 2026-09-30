# Carda release preparation — F20 remains unfinished

Prepared 29 September 2026. This is an execution checklist and configuration handoff, not release approval. All F01–F20 remain requirements. Use the exact candidate's [`requirements-matrix.md`](requirements-matrix.md), [`evidence-index.md`](evidence-index.md), validation reports and final Figma checks before a product-completion claim.

## Current artifact boundary

- `:app:bundleRelease` builds an **unsigned preparation AAB** when no signing environment is supplied. The latest local software/emulator rerun E-20260930-011 and bundletool structural validation passed after the F06/F07/F08/F17/F18 support changes and the F17 next-day regression fix. AAB SHA-256: `be0c39175d142fac94069cc36271b71555246d2b711b6497cbaf1d280c10a003`; `jarsigner` reports unsigned. `:app:assembleRelease` produced unsigned APK SHA-256 `babe274cd1773d15b52dcfdd04bf157e6480c21fa3c8add16c955d805dd98f66`; `scripts/check_android_privacy.py` passed its merged-manifest/backup-rule gate on that APK. No local bundle has a configured production identity endpoint. Packaging success does not make normal account access usable, prove physiology or authorize distribution.
- `:app:assembleDebug` includes the clearly labelled anonymous engineering capture and transient F11/F12 research readout after an accepted capture. The app checks Android's debuggable flag; release does not invoke these debug paths, and ordinary release measurement requires a real signed-in account. A signed release install and accepted physical capture are still needed to verify that boundary end to end.
- CardioGAN weights/fixtures are outside Git. Optional research assets enter only `core:ml`'s instrumentation-test APK; `app` has no dependency on that experimental runtime or production model asset. The model parity test was skipped in E-20260930-009 because those assets were not supplied. F10 and other unsupported metrics stay unavailable in the production capture path.
- A temporary adaptive C launcher mark supports installation/testing. It is engineering artwork, awaiting final Figma approval.
- Source changes are uncommitted. Record an approved source revision before a reproducible distributed build; preserve existing user documentation.

## Versions and signing

Build properties `cardaVersionCode` and `cardaVersionName` default to1 and0.1.0. Increment version code for each Play artifact and associate it with source/build, pipeline, Room schema and model version/hash. Capture reads the actual package version; compatibility/profile/result/export metadata must match the installed build.

The signing owner supplies an existing, securely stored keystore **outside this repository** and all four secret environment variables:

| Variable | Value supplied through an approved secret path |
| --- | --- |
| `CARDA_KEYSTORE_PATH` | Absolute external keystore path |
| `CARDA_STORE_PASSWORD` | Keystore password |
| `CARDA_KEY_ALIAS` | Approved signing alias |
| `CARDA_KEY_PASSWORD` | Key password |

The Gradle configuration rejects partial signing configuration and a keystore within the repository. It does not create a permanent signing identity. Do not paste secret values into command history, logs, artifacts or Git. Key custody, recovery and Play App Signing enrollment need a named human owner.

The same configuration now requires a valid **HTTPS** identity host whenever signing variables are supplied. A disposable fake-key/HTTP configuration was rejected in E-20260930-007; that negative check does not prove a signed artifact or deployed host. Local CI source also builds unsigned release APK/AAB and runs the static privacy check, but the workflow has not run on GitHub.

Once the real HTTPS identity endpoint and signing inputs are supplied, run:

```bash
./gradlew test lint :app:assembleDebug :app:assembleRelease :app:bundleRelease \
  -PcardaAuthBaseUrl=https://YOUR_APPROVED_IDENTITY_HOST \
  -PcardaVersionCode=NEXT_APPROVED_INTEGER \
  -PcardaVersionName=APPROVED_VERSION
./gradlew connectedDebugAndroidTest
python3 scripts/check_android_privacy.py app/build/outputs/apk/release/app-release.apk
```

Replace the tokens with approved actual values; these are not working endpoint/version values. Without external research assets, the opt-in ML research test skips and cannot contribute model evidence. Run phone/reference/usability protocols separately; emulator success cannot substitute for them.

For the exact candidate, validate the AAB with a pinned official [bundletool](https://github.com/google/bundletool/releases), verify its signature/certificate, hash it, build device APKs, install on physical target devices and test account/measurement/offline/failure/export/delete flows. An unsigned AAB's structural validation is a separate evidence item from signed install and release acceptance. Do not upload it to Play as a finished product.

## Privacy and distribution gate

| Item | Concrete evidence before release | Current missing dependency |
| --- | --- | --- |
| Identity | Cloud Run/SQL migration, real SMTP verification/reset, TLS, token rotation/logout/deletion and offline-after-login tests; health/profile data absent from requests/tables/logs | Current clean source has25/25 backend tests and ordinary/adversarial21 plus SMTP-outage/retry12 local statuses E-057/E-058. One Android emulator logged in to local service and reopened offline E-056. Durable mail delivery/retry, timing audit, GCP/SMTP/environment owner and deployed checks remain. |
| Permission/backup | Release merged manifest, scoped network/log audit, backup exclusions; camera only during active session; notifications explicit opt-in | Candidate traffic/physical audit |
| Data safety | Inspect actual client/backend/infrastructure data practices. Email/account IDs leave the device for identity; health/profile/frames/raw PPG remain local. Account/session/security metadata and infrastructure logs must be disclosed accurately | Reviewed hosting/security retention practices |
| Public policy and deletion | Public privacy URL, contact/retention/security information, in-app account deletion and applicable external deletion-request route | The identity service contains `/account-delete/index.html` with local MVC and historical browser/API proof E-040/E-054; browser flow needs current-source rerun. Public HTTPS deployment, approved URL, policy link/contact and actual Play listing are still absent. Portal deletion removes server identity/sessions only; local health data on each phone need separate deletion. |
| Health content | Health-app declaration, clear prototype limits, device compatibility and supported usage conditions; no clinical or detection claim beyond evidence | Expert review, physical/reference reports, Play Console owner |
| Listing/assets | Indonesian description, accurate screenshot/video of real states, final Figma/icon/assets; download links match approved artifact | Figma/contact/download path |
| Closed testing | Actual eligible testers, enrollment dates, feedback and platform status, distinct from technical test results | Play account type/track/testers unconfirmed |
| Maintenance | Owner, current candidate hashes, open defects, model/pipeline rollback and migration plan; preserve local records on upgrades | Operational owner/signing custody |

API36 is the current minimum target for new ordinary mobile submissions under the [Google Play target API policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en), checked 29 September 2026. [Health content rules](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en) and [Android release guidance](https://developer.android.com/build/build-for-release) inform this gate; recheck platform requirements at submission. Policy preparation is not a declaration submitted or a Play approval obtained.

## Candidate record / rollback

Record: source revision and uncommitted-state disposition; version code/name; artifact SHA256/bytes; signing certificate fingerprint (never private key); endpoint environment; min/target SDK; pipeline/threshold/model versions; Room schema; tested devices/conditions and evidence IDs; outstanding blockers; reviewer/approval date; track/review status.

Rollback must retain signing identity and use a new higher version code. Check Room migration compatibility before shipping a code rollback; do not downgrade schema or silently discard history. Model rollback restores an approved hash/preprocessing/evidence set together; no available release model exists yet. Halt rollout for failed-SQI output, lifecycle leaks, privacy leakage or crashes, document affected evidence and rerun relevant verification before continuation.
