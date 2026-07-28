# Architecture

## Decision

Build Carda as a native Android Kotlin application with a thin `app` entry module, feature modules for user journeys, and core modules for reusable platform and domain behavior.

The architecture follows UI -> domain/use cases -> repository/platform adapters. UI state flows in one direction. Hardware and database details do not leak into a feature UI.

## Module responsibilities

| Area | Owns | Must not own |
| --- | --- | --- |
| `app` | Application setup, dependency graph, root navigation | Feature business logic |
| `feature:*` | Screen state, user events, use-case orchestration | CameraX/Room/LiteRT calls |
| `core:camera` | Camera lifecycle, flash, frame delivery, capability probe | Health metrics |
| `core:ppg` | Sample extraction, filters, SQI, deterministic metrics | UI or persistence |
| `core:ml` | Model loading and inference contract | Camera frame capture |
| `core:data` | Local persistence and repositories | UI state |
| `core:designsystem` | Shared visual components | Feature-specific logic |

## Measurement flow

```text
Onboarding / preflight
  -> measurement guidance
  -> CameraX preview + image analysis
  -> frame validity
  -> PPG signal window
  -> SQI gate
  -> deterministic metrics
  -> optional experimental inference
  -> result, retry, or unsupported state
```

## Interfaces

Use interfaces to make stateful boundaries testable:

- `CameraSessionController`
- `DeviceCapabilityRepository`
- `PpgSignalProcessor`
- `SignalQualityEvaluator`
- `MeasurementRepository`
- `ExperimentalInsightRunner`

Prefer a single source of truth for measurement state. Raw frames must not reach a ViewModel or persistence layer.

## References

- [Android architecture](https://developer.android.com/topic/architecture/recommendations)
- [Android modularization](https://developer.android.com/topic/modularization)
- [CameraX architecture](https://developer.android.com/media/camera/camerax/architecture)
