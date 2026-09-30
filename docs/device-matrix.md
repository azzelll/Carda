# Carda device compatibility matrix

Created 29 September 2026. **No physical phone has been tested or classified in this matrix.** A directory name, Android SDK, emulator or nominal 720p/30 fps capability is not a capture/probe result. This file records only aggregate, non-sensitive compatibility observations; never attach camera frames or raw PPG.

## Classification contract

- `compatible`: required rear RGB camera and torch available, bounded preview/analysis stream succeeds, configured capture window and observed cadence/illumination/contact/SQI pass on the recorded configuration, with no lifecycle or heat stop condition during the tested session.
- `restricted`: session or configuration works only under documented limits, or a required metric has insufficient hardware/validation coverage. Specify allowed mode and reason; never silently lower SQI.
- `not_supported`: camera/torch absent or required stream/probe cannot pass safely. Show reason and retry/support guidance.

Record nominal proposal checks (Android API ≥26; ARM64 four cores; RAM ≥3 GB; free storage ≥250 MB; 720p at 30 fps) separately from **observed** stream and signal classification. Hardware specifications are planning filters, not physiological validation. Exposure/flash state is recorded when available. A fallback resolution/FPS must pass the same signal gate. Versions and thresholds are needed to make the classification reproducible.

The current CameraX metadata snapshot records the exposure **compensation** index and EV offset when the camera reports them, plus the observed torch state when the camera becomes ready. It does not measure sensor exposure time, gain, per-frame auto-exposure behavior, or guarantee the torch remains on. The per-frame torch flag and signal-quality gate still determine whether a window can be accepted. A missing metadata value stays unknown; physical-device testing must check actual behavior.

| Device ID | Manufacturer/model | API/ABI, CPU/RAM/free storage | Build, pipeline/SQI version | Rear RGB/torch; nominal 720p/30 | Selected analysis size; observed cadence; exposure/flash | Attempts accepted/rejected and reasons; SQI | Lifecycle/thermal notes | Classification and tested limits | Evidence ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| *No device tested yet* | — | — | — | — | — | — | — | — | — |

## Emulator observation (excluded from physical-device count)

On 29 September 2026, `Medium_Phone_API_36.1` (Android API 36.1, `sdk_gphone64_arm64`) installed a debug APK with SHA-256 `8c7e7dfb2df2bb4591ea1e7d1bb2cb519d61ebecd62fed3fafeae3c01ab84f59`. The emulator exposed a rear camera but **no torch**. Carda showed `not_supported` with “Lampu kilat kamera belakang tidak tersedia.” and nominal hardware limits, then returned to the start screen. The profile displayed the saved classification and reason. This is E-20260929-015; E-20260929-005 records the earlier build. A later v2 APK (`4eaa712087a7e5a63846dcc36d9090c8859d7a3a5267d9d3ffa652c1a57ad7b8`) was installed for activity-choice UI E-018, without repeating the no-torch capture flow. No valid finger probe, frame-cadence classification, HR or thermal/lifecycle inference is drawn from the emulator.

## Physical test procedure

1. Record device ID without user identity, manufacturer/model, OS/API/ABI, app/pipeline/SQI versions, nominal capabilities and chosen CameraX stream. Verify rear camera and torch at runtime.
2. Perform short probe then a valid-window attempt. Observe FPS distribution/dropped timestamps, resolution, exposure/torch state, latency, heat and whether the torch/camera release on cancel, background, timeout, error and normal finish.
3. Try insufficient illumination, bad placement/coverage, saturation, movement, clipping/irregular cadence where controllable. Record every attempt, reason, SQI and whether any number incorrectly appears after rejection.
4. Confirm capture graph PPG is the actual transient sample trace and that no frame/raw PPG entered persistence, network, backup or logcat. Record only aggregate diagnostics here.
5. Classify with explicit tested conditions. Repeat after camera configuration, thresholds, pipeline or relevant OS/build changes; flag old evidence `requires-rerun` in [`engineering/evidence-index.md`](engineering/evidence-index.md).

Pilot target from [`final-feature-scope.md`](final-feature-scope.md): 5 Android devices from at least 3 manufacturers if available, with one restricted/not-supported profile. The target is not a completed count or a universal compatibility claim. Physical devices and a test owner remain unconfirmed.
