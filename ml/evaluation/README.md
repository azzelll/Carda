# Offline respiration-method exploration

`bidmc_respiration_baseline.py` evaluates a **candidate** low-frequency PPG method against the two manual breath annotators in the [BIDMC PPG and Respiration Dataset v1.0.0](https://physionet.org/content/bidmc/1.0.0/). The dataset is published under Open Data Commons Attribution License v1.0; cite Pimentel et al. and PhysioNet as requested on the source page. Signals and annotations are downloaded to a team-approved location **outside Git**. No participant-level input or raw waveform is committed here.

For example, download the `bidmc_01_Signals.csv` and `bidmc_01_Breaths.csv` files from the dataset's `bidmc_csv/` directory, then run:

```bash
python3 -m unittest discover -s ml/evaluation -p 'test_*.py'
python3 ml/evaluation/bidmc_respiration_baseline.py --csv-dir /path/outside/repo --record 01
```

The deterministic `bidmc-subject-v1` manifest reserves previously inspected records 01/02 for development, groups recordings with the same MIMIC matched waveform ID, and lists only public record numbers and aggregate group counts. Metadata IDs, demographics and source files remain outside Git. Reproduce the **development-only** report with:

```bash
python3 ml/evaluation/fetch_bidmc_metadata.py --metadata-dir /path/outside/repo
python3 ml/evaluation/bidmc_subject_split.py --metadata-dir /path/outside/repo --output ml/evaluation/bidmc_split_manifest.json
python3 ml/evaluation/fetch_bidmc_development.py --metadata-dir /path/outside/repo
python3 ml/evaluation/bidmc_development_report.py --csv-dir /path/outside/repo
```

The repository report [`bidmc_dev_baseline_0.1.json`](bidmc_dev_baseline_0.1.json) contains only aggregate exploratory statistics. The 15 validation and 27 held-out recordings have **not** been downloaded as Signals/Breaths or evaluated. A named study lead must review the grouping/protocol and freeze method, gates and acceptance criteria before either reserve is used. Matching a waveform ID is a grouping safeguard, but any additional patient identity overlap must be checked before claiming patient-independent evaluation.

The evaluation dependencies are pinned in `requirements.txt` for the tested Python 3.13 environment. Verify the dataset files against PhysioNet's `SHA256SUMS.txt` before interpreting a run. The fixed 60-second window, 30-second step and 0.1–0.6 Hz filter are exploratory parameters, **not** frozen acceptance thresholds. The parser treats trailing `NaN` values as padding in independently sized annotator columns and rejects internal gaps. Overlapping windows within a recording are not independent people or independent sessions. The baseline uses clinical pulse-ox PPG and has no respiratory-specific SQI, so zero computational rejections is not an acceptance-rate claim. F12 remains unavailable in the Android result until a method, camera-domain comparison, reference protocol, rejection gate and reviewed usage conditions are supported.

### Android-side research candidate `rr-research-0.1`

`core:ppg` now contains an **internal** pure-Kotlin `ResearchRespirationEstimator` for software experiments. It requires a current passing global SQI report and recomputes SQI over one private, bounded snapshot of the input. The caller must retain ownership of its capture list while that snapshot is copied and construct the estimator with the **same configured `PpgPipeline` instance** that produced the supplied report; a default evaluator cannot safely stand in for device-specific SQI settings. A changed list during the copy or a report/version mismatch is withheld. Its separate RR gate requires 58–70 seconds, at least 20 frames/s, at most 7,200 samples, at most 10% worst-frame cadence jitter, and at most 2% samples with the configured motion/contact/clipping/saturation/torch artifact flags. A 60-second window with more than 7,200 observations is withheld by this RR-specific sample budget; that does not label the global capture invalid. The proposed thresholds are configurable and versioned but **not** validated against breathing references or phone-camera recordings.

The estimator linearly detrends red-channel samples, measures Hann-weighted sinusoidal projections in the 0.1–0.6 Hz range, and requires minimum component amplitude and contrast, one dominant frequency, and agreement across two halves of the window. It returns a research-only candidate or a specific unavailable reason. This projection is **not** a port or performance replication of the Python Butterworth/periodogram baseline. Synthetic tests cover steady rates, short windows, cadence, artifacts, pure pulse plus harmonic, changing respiratory modulation, stale SQI reports, single-read input ownership, configurable SQI matching, and input budget. An amplitude-only respiration fixture, where breathing varies pulse amplitude without a low-frequency baseline component, is withheld as weak modulation: this method does **not** cover that PPG breathing mechanism. A matching SQI report cannot prove physiological validity or identity of a mutable capture; producers must keep the capture stable through the snapshot step. No candidate is connected to `MetricResult`, app result/history/export, or a claim about measured respiration. Comparison against breathing references and camera-domain rejection/accuracy remains outstanding.

## F11 red/green optical feature research

`core:ppg` contains `ResearchOximetryFeatureExtractor`, an internal `spo2-feature-research-0.1` feature extractor. It computes a **unitless** ratio of pulse-frequency AC/DC components in camera red and green channels after current global SQI, HR and additional channel/stability gates. It does **not** map that ratio to a saturation percentage. Six synthetic unit tests E-059 check a stable ratio, global rejection, absent green pulsatility, unstable half-window ratios, stale quality and insufficient duration. A debug-only accepted-capture note can now display this ratio and the independently gated F12 research candidate E-060; neither is persisted/exported or put in `MetricResult`, and a 30-second capture withholds RR for insufficient duration. There is no physical accepted-capture readout yet. This code gives the team's F11 method lead a deterministic starting feature for a device-specific paired study; no participant observations or calibration coefficients have been added. The [smartphone-camera calibration study](https://www.frontiersin.org/journals/digital-health/articles/10.3389/fdgth.2023.1301019/full) motivates per-device calibration but does not validate this extractor or a Carda output. Before adding a percent result, collect approved paired phone/reference data outside Git, separate calibration from evaluation participants/sessions, record exposure/device/range and rejection yield, and version the mapping and supported conditions.

## CardioGAN CPU conversion exploration

See [`model-register.md`](../../docs/engineering/model-register.md) for official source/license, pinned commit, training overlap and remaining blockers. No weights or upstream code are vendored. The research source is CC-BY-NC4.0; deployment/distribution has no approval. Restoration uses isolated Python3.12 with TensorFlow2.21.0/tf-keras2.21.0, not the RR environment above. After downloading the inventoried source and safely extracting the official checkpoint **outside this repository**:

```bash
python ml/evaluation/cardiogan_checkpoint_probe.py \
  --source /outside/repo/cardiogan --checkpoint /outside/repo/cardiogan/weights/ckpt-13 \
  --scratch /outside/repo/cardiogan --output /outside/repo/conversion-report.json \
  --tflite /outside/repo/research-model.tflite
python ml/evaluation/cardiogan_android_fixtures.py \
  --model /outside/repo/research-model.tflite --conversion-report /outside/repo/conversion-report.json \
  --output /outside/repo/android-research-assets
./gradlew :core:ml:testDebugUnitTest :core:ml:lintDebug :core:ml:connectedDebugAndroidTest \
  -PcardaResearchAssets=/outside/repo/android-research-assets
```

The four sine inputs and host reference traces are synthetic and stay in external scratch/generated test build assets. Gradle permits them only in the debug instrumentation-test APK. Omitting the property clears generated assets and skips the opt-in research test; **a skip is not model evidence**. Normal missing/hash-invalid artifact tests still run. Aggregate CPU/conversion/emulator reports are in `reports/`. The ARM64 AVD crashed with XNNPACK enabled; conservative CPU kernels disabled it and passed frozen1e−5 parity. No measured ECG, confidence, R-peak, physiological number or camera-domain validity is produced by this smoke test. Production Android ECG Insight remains unavailable.
