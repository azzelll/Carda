# ECG Insight model inventory

Checked 29 September 2026. No model is currently a release-ready Carda asset. Downloading weights or producing a waveform does not satisfy F10. Research artifacts remain in temporary scratch, outside Git and the APK.

## Candidate M01 — CardioGAN

- Primary sources: [author repository](https://github.com/pritamqu/ppg2ecg-cardiogan), [paper](https://arxiv.org/abs/2010.00104), [checkpoint release](https://github.com/pritamqu/ppg2ecg-cardiogan/releases/tag/checkpoints). Source commit inspected: `d7239b0004a138101bdf597961be4d0af8ea2d5e`. Six source/license files were downloaded for inspection; hashes are in `ml/evaluation/cardiogan_source_inventory.json`.
- [License](https://github.com/pritamqu/ppg2ecg-cardiogan/blob/main/LICENSE): CC BY-NC 4.0. Research use and eventual distribution context require a recorded license decision and attribution. No license change or permission from the authors has been obtained.
- The release ZIP is `cardiogan_all_models.zip`, 1,802,015,513 bytes; upstream metadata supplies no asset digest. Downloaded to `/private/tmp/carda-cardiogan-inventory/`: local SHA-256 `585f6615573831cf69a56d81b9ae2ff61e1c23b7f340e10912257eed54a75f98`. All ZIP member CRCs passed; five checkpoint-only entries (1,894,339,492 uncompressed bytes) were path-checked and extracted only in scratch. This is local integrity/provenance recording, **not comparison against an upstream digest**. No inference or APK integration was completed at this archive checkpoint.
- Author code uses 128 Hz, four-second windows, 512 input samples, a fourth-order 1–8 Hz PPG bandpass, per-window normalization to −1…1 and a generated waveform. Example sets float64 and references TensorFlow2.2-era APIs. The generator is an attention encoder/decoder; its output is **not a measured ECG**. Resampling 20–30 fps camera data to128 Hz cannot recreate missing temporal information.
- There is no calibrated confidence output in the inspected generator. A discriminator score, SQI, waveform resemblance or plausible beat rate must not be silently relabelled as calibrated model confidence.
- README states training used BIDMC/CAPNO/DALIA/WESAD. These datasets cannot automatically be claimed independent evaluation for the supplied checkpoint. In particular, Carda's reserved BIDMC RR split does not establish ECG checkpoint independence. Sensor PPG training also does not validate Carda camera PPG.

### Required work before product eligibility

1. Inspect the archive safely, pin checkpoint files/hash, and confirm which generator and training provenance the chosen checkpoint represents. Document preprocessing/polarity, shapes, output units and normalization.
2. Reproduce CPU inference in an isolated compatible environment. Audit the sample code before execution. Record restoration checks, deterministic output, failures and peak memory; do not use uninitialized or partially restored weights as model results.
3. Convert a named generator to LiteRT only after restoration is verified. Compare CPU outputs against the source framework at a frozen numerical tolerance, measure size/latency/RAM, and test unsupported/missing/hash-invalid artifacts. A successful conversion is software evidence only.
4. Freeze an independent, subject-separated ECG evaluation protocol with justified training-overlap handling. Evaluate waveform/timing, R-peaks, mean cardiac R–R interval and failure/coverage separately; assess confidence calibration. Do not tune on reserved final data.
5. Test synchronized **Carda camera–reference ECG** under stated conditions, including rejection and uncertainty; obtain qualified review of waveform/category copy. Preserve all proposal categories as unmet criteria when evidence is insufficient.
6. Release requires a model card, license/attribution decision, version/hash, preprocessing and pipeline compatibility, confidence threshold justified by evaluation, explicit disclaimer, safe CPU fallback and rollback. Model absence/failure must leave independently valid HR available.

### Technical feasibility checkpoint — E-034/E-035

The source generator was restored with all existing objects matched under isolated Python3.12/TensorFlow2.21/tf-keras2.21. Only unused opposite-generator/discriminator/optimizer objects were omitted. Its28,237,255 parameters are float64. The adapter changes Input shape notation to a tuple and explicitly rejects the unused instance-normalization branch; default layer normalization is preserved. CPU inference was finite, shape1×512 and deterministic on a synthetic sine fixture.

The float32, built-in-ops LiteRT artifact is112,990,672 bytes, SHA-256 `4cbc5a4c7cb57edac950129e5ce4e34d32334e182801e03b7fa8027c475341bb`. Four synthetic fixtures passed the predeclared1e−5 absolute software tolerance: max float64→float32 error5.64e−7, float32→host Lite error1.68e−6. No participant traces were used or exported. Scripts and aggregate reports are under `ml/evaluation/`; model/source/fixture binaries remain in external scratch.

Bundled LiteRT2.2.0 CPU was tested on the API36.1 ARM64 emulator. XNNPACK initialization crashed with native SIGILL during tensor allocation; disabling XNNPACK alone allowed the same artifact to pass. Conservative one-thread CPU is the default in `core:ml`; this is not a successful hardware acceleration fallback test on physical phones. Four host/Android synthetic comparisons had max absolute difference5.96e−7; observed load247ms, calls105/42/20/22ms, maximum **sampled** total-process PSS258,400KiB. PSS is not peak model RAM, and emulator timings are not phone performance.

`core:ml` checks file bytes/hash, float32 tensors, shape/finite output and off-main work. Its release runner gates SQI, preprocessing/pipeline, reviewed report references, exact evaluated device/ABI/resolution/cadence/conditions, distribution approval and calibrated confidence. Cancellation during loading prevents a subsequent inference and closes the acquired backend; a synchronous native call already underway cannot be interrupted. Twelve unit tests and two emulator tests passed. Research assets can enter only an opt-in instrumentation-test APK, never production assets. The module is not connected to user ECG output.

### Research timing and conditional presentation checkpoint — E-20260930-001/004

`core:ml` now extracts **candidate** R-peak sample positions and mean cardiac R–R milliseconds from an already SQI/device/model/confidence-gated estimated waveform. `ecg-timing-research-0.1` uses configurable polarity, prominence, signal-to-RMS, refractory and interval checks; all are provisional software heuristics. Positive/inverted synthetic spikes, flat/invalid inputs, close artifacts and unsupported intervals passed focused unit fixtures. The result UI has an optional horizontally scrollable **estimated** waveform, peak markers, R–R text and prominent PPG-derived disclaimer. A synthetic emulator UI test passed; no production capture path supplies an available insight, and waveform/timing never enters the persisted `MeasurementSummary` or PDF. Current CardioGAN has **no calibrated confidence** and therefore cannot reach this UI through the release runner.

Before enabling F10, freeze and evaluate this timing method against independently sourced ECG beat annotations and synchronized **Carda phone-camera–ECG** pairs, including polarity failures, missed/extra peaks, window boundaries, device/condition yield and mean R–R error. A plausible synthetic timing trace is not ECG beat evidence. The candidate's parameters, model and UI copy would require versioned re-evaluation after changes.

Current status: **research CPU restoration/conversion/emulator parity tested and synthetic timing/presentation tested; license distribution decision, calibrated confidence, physiological validity, camera domain, R-peak/R–R evaluation and real-phone performance unfinished**. A waveform-only checkpoint supplies no confidence and cannot pass the product gate. F10 remains unavailable in Android; no synthetic ECG or diagnostic category is substituted for it. See E-031–035 and E-20260930-001/004.

## Candidate M02 — Elgendi PPG2ECG

The [paper's linked repository](https://github.com/Elgendi/PPG2ECG) was inspected at the directory/README level. It contains source and `Records.mat`, but no license file or release-ready mobile checkpoint was visible in that inspection. No data/code were imported or executed. Clarify reuse rights and subject-specific evaluation requirements before adopting this route; source availability alone is insufficient.
