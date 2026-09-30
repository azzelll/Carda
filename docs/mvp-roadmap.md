# MVP Roadmap

This document describes the foundation milestones. The [final feature scope for 30 October 2026](final-feature-scope.md) records the full proposal inventory and the user's 17 September instruction: do not remove features unless explicitly requested by the judges. Milestone order does not authorize dropping unfinished features. Quality, privacy, and evidence requirements still apply.

## Release 0 — foundation

- Kotlin Android project, Compose, navigation, Hilt, baseline CI.
- Design system, consent screen, privacy language, camera permission flow.
- Device profile and compatibility data models.

**Exit:** app builds, static screens are navigable, and privacy/safety copy is reviewed.

## Release 1 — compatible capture

- CameraX preview and rear-camera/torch preflight.
- Frame cadence, image validity, torch/error state handling.
- Guided capture UI with cancellation and retry.

**Exit:** supported and unsupported paths work on at least two device profiles without camera lifecycle leaks.

## Release 2 — PPG quality and heart rate

- ROI/channel extraction, timestamping, filtering, windowing.
- SQI and clear rejection reasons.
- Heart-rate result only when the quality gate passes.

**Exit:** fixture tests cover invalid signals; no result appears after a failed quality gate.

## Release 3 — local history

- Room schema, local history, delete flow, preference storage.
- Pipeline and device-profile version attached to every result.

**Exit:** history survives app restart, deletion is explicit, and no raw PPG/frame is persisted.

## Release 4 — experimental insight

- Validated LiteRT/TFLite integration behind a feature flag.
- Model card, confidence threshold, model/pipeline version, CPU fallback.
- Experimental disclaimer and no-diagnosis wording.

**Exit:** inference is gated by SQI, model availability, and confidence; failure never blocks the baseline measurement flow.

## Scope beyond the original MVP

Proposal-listed account flows, additional physiological estimates, profile functions, export, and other final deliverables remain tracked in the final feature scope, including unresolved inconsistencies and validation dependencies. They must not be silently removed based on the narrower original MVP. Cloud sync, social features, wearables, and continuous background monitoring are not added implicitly. Diagnostic claims and unvalidated numerical outputs remain disallowed.
