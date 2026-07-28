# Carda — AI Engineering Context

This repository is the Android application for **Carda**, a privacy-first cardiac wellness prototype. Carda uses the rear camera and flashlight to acquire fingertip photoplethysmography (PPG), estimates non-diagnostic metrics locally, and communicates signal quality and uncertainty clearly.

This is a competition and research prototype. It is **not** a medical device, emergency service, or diagnostic tool.

## Read first

Before changing code, read the relevant document:

| Topic | Required reference |
| --- | --- |
| System boundaries and module ownership | [docs/architecture.md](docs/architecture.md) |
| Delivery scope and acceptance criteria | [docs/mvp-roadmap.md](docs/mvp-roadmap.md) |
| Camera, PPG, quality, privacy, and safety rules | [docs/ppg-quality-and-safety.md](docs/ppg-quality-and-safety.md) |

When a task conflicts with a safety rule in this file, follow the safety rule and explain the trade-off in the pull request or handoff note.

## Product principles

1. **On-device by default.** Frames, raw PPG, and derived measurements stay on the device unless a user-approved feature explicitly says otherwise.
2. **Quality before output.** A low-quality signal never produces a numerical result, trend point, or ECG Insight.
3. **No diagnosis.** Use language such as “informasi”, “indikator”, and “tidak dapat ditentukan”; never claim detection, diagnosis, treatment, or clinical accuracy without validated evidence.
4. **Device-aware.** Android camera hardware varies. Check flash, rear camera, FPS, exposure behavior, and signal quality at runtime.
5. **Explain uncertainty.** The UI must show why a capture failed and how the user can retry.
6. **Small, testable changes.** Keep feature logic isolated, preserve module boundaries, and add tests with every behavior change.

## Target stack

- Kotlin, Jetpack Compose, Material 3, single-activity navigation.
- CameraX `Preview` + `ImageAnalysis` for concurrent preview and frame analysis.
- Coroutines and `Flow`; state is immutable and follows unidirectional data flow.
- Hilt for dependency injection.
- Room for measurement history; DataStore for preferences and consent flags.
- LiteRT/TFLite for optional experimental inference only after signal-quality validation.
- Gradle Kotlin DSL with a version catalog and convention plugins when build logic starts repeating.

Use current stable versions when adding a dependency. Do not add a dependency merely to avoid writing a small, well-tested utility.

Official references:

- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- [Android app modularization](https://developer.android.com/topic/modularization)
- [CameraX architecture](https://developer.android.com/media/camera/camerax/architecture)
- [Jetpack Compose](https://developer.android.com/develop/ui/compose)
- [LiteRT on Android](https://ai.google.dev/edge/litert/android/java)
- [Android privacy and security](https://developer.android.com/privacy-and-security)

## Intended repository layout

```text
app/                         Android application entry point and root navigation
core/
  common/                    Result types, dispatchers, shared utilities
  designsystem/              Theme, typography, reusable Compose components
  model/                     Stable domain models with no Android dependencies
  data/                      Repository implementations, Room, DataStore
  camera/                    CameraX, torch, device capability checks
  ppg/                       Frame-to-signal pipeline, filtering, SQI, HR/HRV
  ml/                        LiteRT model runner, model metadata, confidence gate
  testing/                   Fakes, fixtures, test helpers
feature/
  onboarding/                Consent, limitation education, compatibility preflight
  measurement/               Guided capture and real-time quality feedback
  result/                    Result presentation, disclaimer, retry state
  history/                   Local history and trends
  profile/                   Preferences and privacy controls
ml/
  models/                    Release-ready model artifacts only; no training data
  evaluation/                Reproducible offline evaluation scripts and reports
docs/                        Architecture, roadmap, safety, ADRs, and device matrix
```

### Dependency direction

```text
app -> feature:* -> core:domain contracts / core:ui
                   -> core:data / core:camera / core:ppg / core:ml
core:data -> core:model
core:ppg -> core:model
core:ml -> core:model
```

- A feature must not access CameraX, Room, DataStore, or LiteRT directly.
- `core:camera` emits frames/capability events; it does not calculate health metrics.
- `core:ppg` owns deterministic signal processing and Signal Quality Index (SQI).
- `core:ml` receives a validated signal window, never raw camera frames.
- Use interfaces in the domain-facing layer and implementations in `core:data` or the hardware modules.
- Do not introduce a separate Gradle module until there is a clear ownership boundary or reusable behavior. Avoid modularization theater.

## MVP scope

### In scope

1. Onboarding with consent, limitations, and camera permission rationale.
2. Device preflight: rear RGB camera, torch/flash availability, supported analysis resolution/FPS, and a short signal probe.
3. Guided fingertip capture with torch control and a visible quality state: `preparing`, `measuring`, `too_dark`, `motion_detected`, `poor_contact`, `processing`, `complete`, or `retry`.
4. Deterministic on-device PPG preprocessing, SQI gate, and heart-rate result when quality passes.
5. Local, user-visible history with delete-all capability.
6. Device compatibility record and structured diagnostic metrics that contain no frames or raw PPG.
7. Experimental ECG Insight only when a validated model, explicit disclaimer, model version, confidence threshold, and an SQI pass are all present.

### Explicitly out of scope for MVP

- Clinical diagnosis, arrhythmia detection claims, treatment guidance, or emergency triage.
- Uploading camera frames or raw PPG to a server.
- Social features, sharing, wearable integrations, continuous background measurement, or multi-platform clients.
- SpO2, blood-pressure, and other unvalidated estimates.
- Training models on-device.

## PPG pipeline contract

```text
CameraX ImageAnalysis frame
  -> frame validity checks
  -> ROI / channel intensity extraction
  -> timestamped PPG samples
  -> detrend + band-pass / smoothing
  -> motion / saturation / clipping checks
  -> Signal Quality Index
  -> valid window only
  -> HR / HRV calculation
  -> optional LiteRT inference with confidence gate
  -> displayed result or retry instruction
```

Implementation rules:

- Analyze a bounded stream. Close every `ImageProxy` exactly once, even on errors.
- Keep capture, extraction, filtering, SQI, feature calculation, and presentation as separate units.
- Make thresholds configurable and documented; never hide them as unexplained constants.
- Persist only a summarized result by default: time, metric values, quality score, device profile, pipeline/model version, and user-visible status.
- Never convert a failed SQI into a zero or a guessed health value.
- If a delegate/model is unsupported, fall back safely or hide the experimental feature; do not crash or silently change semantics.

## Camera and device compatibility

The runtime must derive, persist, and display a `DeviceProfile` with at least:

- Manufacturer, model, Android API level, ABI, app version.
- Rear-camera and torch availability.
- Selected analysis resolution and observed frame cadence.
- Exposure/flash state when available.
- Capture rejection reason, SQI, and final support classification: `compatible`, `restricted`, or `not_supported`.

Do not hard-code an all-device success assumption. A capture can be rejected for saturation, insufficient illumination, unstable frame cadence, excessive movement, poor finger coverage, or quality below threshold.

## Agent playbooks

Choose the smallest relevant playbook before implementation.

### Android feature playbook

- Begin with a state/event/effect sketch.
- Keep composables stateless where possible; state belongs in a screen-level ViewModel.
- Use a sealed UI state and one-shot effects only for transient navigation or permission prompts.
- Add accessibility labels, content descriptions, and preview states for loading/error/success.

### Camera and PPG playbook

- Build a deterministic pure-Kotlin signal pipeline before connecting it to CameraX.
- Add fixture-based unit tests for normal, dark, saturated, clipped, motion-heavy, and irregular-cadence signals.
- Use realistic device testing before tightening thresholds.
- Log only aggregates; never write frames or raw PPG to analytics/logcat.

### ML inference playbook

- Treat every model as versioned product content. Add a model card, expected input/output contract, validation metric, limitations, and rollback plan.
- Run inference off the main thread.
- Gate inference on valid input shape, signal quality, model availability, and confidence threshold.
- Ensure CPU fallback is safe. Hardware acceleration is optional, not a requirement for correctness.

### Data and privacy playbook

- Define repository interfaces around use cases, not database tables.
- Make destructive history deletion explicit and test it.
- Keep consent/version migrations backward compatible.
- Do not add cloud sync, telemetry SDKs, or identifiers without an explicit product decision and privacy review.

### Quality-assurance playbook

- Unit-test reducers, use cases, signal utilities, and repository behavior.
- Add instrumented tests for CameraX integration and Room migrations where applicable.
- Test at least one compatible and one restricted device profile.
- Treat crashes, capture lifecycle leaks, and output after a failed SQI as release blockers.

## Definition of done

A change is complete only when:

- It has a clear owner module and does not violate dependency direction.
- User-facing states include loading, denied permission, unsupported device, low-quality signal, error, and retry where relevant.
- Sensitive data is not emitted in logs, crash reports, screenshots, or fixtures.
- Unit/instrumented tests appropriate to the change pass.
- Relevant documentation and model/device compatibility records are updated.
- The wording makes no diagnostic or clinical claim beyond the available validation evidence.

## Git workflow

- Work on a short-lived branch: `feat/measurement-flow`, `fix/sqi-gate`, `docs/mvp-roadmap`.
- Use conventional commits: `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `build:`.
- Keep commits focused. Do not mix formatting churn, dependency upgrades, and behavioral changes.
- Pull requests should state: scope, device(s) tested, tests run, privacy impact, user-visible behavior, and any model/pipeline threshold change.
- Never commit secrets, APKs, raw datasets, frames, raw PPG, or production user records.

## Important decisions to preserve

- Android native Kotlin is the first client. Do not introduce iOS/web parity work during MVP.
- A backend is intentionally absent from MVP. Add one only for a user-approved need such as opt-in backup or research data collection.
- Carda displays wellness-oriented insights, not diagnoses.
- The model output is subordinate to capture quality and the explicit experimental disclaimer.

## Commands once the Android build is initialized

```bash
./gradlew test
./gradlew lint
./gradlew connectedDebugAndroidTest
```

Run the narrowest relevant task during iteration, then run the applicable quality checks before handoff.
