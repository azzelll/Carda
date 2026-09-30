# Carda

Android prototype for private, on-device fingertip PPG wellness measurements.

Carda is a competition/research prototype, not a medical device or diagnostic service. Read [AGENTS.md](AGENTS.md) before building; its product, safety, and architecture constraints are mandatory.

## Start here

- [Architecture](docs/architecture.md)
- [MVP roadmap](docs/mvp-roadmap.md)
- [Final feature scope — 30 October 2026](docs/final-feature-scope.md)
- [Engineering loop execution prompt](docs/engineering-loop-prompt.md)
- [PPG quality and safety](docs/ppg-quality-and-safety.md)
- [Team Android testing handoff](docs/engineering/team-testing-handoff.md) — build, USB phone setup, local test identity, test order, and evidence format

## Current engineering stack

Kotlin, Jetpack Compose, CameraX, Room, DataStore, Hilt, coroutines/Flow, and LiteRT/TFLite for gated experimental inference.

Software and emulator evidence is summarized in [the evidence index](docs/engineering/evidence-index.md). Physical-device, physiological-reference, usability and signed-release evidence remain separate gates; a green build does not establish them.
