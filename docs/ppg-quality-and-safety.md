# PPG Quality, Privacy, and Safety

## Signal-quality gate

Every result must pass a documented Signal Quality Index (SQI). Minimum rejection checks:

- no rear camera or torch;
- low illumination or inactive torch where required;
- overexposed/saturated pixels;
- inadequate finger coverage or unstable region of interest;
- irregular frame cadence or insufficient valid samples;
- clipping, excessive noise, or motion artifact;
- score below the validated SQI threshold.

When a check fails, show a retry instruction. Never show a health number as a fallback.

## Device variability

Store a compatibility profile containing device/OS/app/pipeline version, selected camera stream characteristics, capture quality, and support classification. Use it to improve testing and assist the user; it must not be presented as clinical validation.

## Data handling

- Do not save or transmit camera frames or raw PPG by default.
- Do not emit frames, raw samples, or measurements to logcat or analytics.
- Store only the minimum summarized result needed for local history.
- Provide a clear delete-history action.
- Treat any future backup/research upload as a separate opt-in product decision with consent, retention policy, and security review.

## Copy rules

Allowed: “kualitas sinyal belum memadai”, “hasil informasi”, “coba ulangi pengukuran”, “fitur eksperimental”.

Not allowed: “mendeteksi aritmia”, “mendiagnosis”, “normal/abnormal”, “aman”, “darurat”, “akurasi klinis”, or treatment advice without the corresponding regulatory and validation basis.

## References

- [CameraX image analysis](https://developer.android.com/media/camera/camerax/analyze)
- [LiteRT Android](https://ai.google.dev/edge/litert/android/java)
- [Android privacy and security](https://developer.android.com/privacy-and-security)
