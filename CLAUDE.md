# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@AGENTS.md

`AGENTS.md` is the authoritative engineering context for Carda. Read the relevant reference before implementation:

| Work area | Required reference |
| --- | --- |
| System boundaries and module ownership | [docs/architecture.md](docs/architecture.md) |
| MVP scope and acceptance criteria | [docs/mvp-roadmap.md](docs/mvp-roadmap.md) |
| Camera, PPG, SQI, privacy, or health-copy changes | [docs/ppg-quality-and-safety.md](docs/ppg-quality-and-safety.md) |

## Design reference

The [earlier Carda Figma design — node 250:510](https://www.figma.com/design/81OQ54yFyhBL8wSYP7BCnl/Carda---Gemastik-PPL?node-id=250-510&m=dev&t=5FnCXPqYS5YgYBaR-1) is historical. Updated designs will be supplied later; do not treat this node as final UI acceptance evidence or request Figma access for engineering and testing that can proceed now.

## Current repository status

The Android Gradle build, identity service, landing source and CI workflow exist. Their latest recorded software evidence and remaining gaps are in [docs/engineering/evidence-index.md](docs/engineering/evidence-index.md) and [docs/engineering/progress.md](docs/engineering/progress.md). A teammate should start with [docs/engineering/team-testing-handoff.md](docs/engineering/team-testing-handoff.md), build an APK from the checked-out source and record its hash. Physical-device and physiological validation have not been established by the software suite.

## Guardrails

- Do not persist, transmit, log, or add to fixtures camera frames or raw PPG unless an explicitly authorized reviewed change permits it.
- A failed SQI must produce retry or unsupported state, never a health number, zero, guess, trend point, or insight.
- Carda is not a medical device: do not make diagnostic, clinical-accuracy, emergency, or treatment claims.
- Feature UI orchestrates through interfaces. Keep CameraX in `core:camera`, deterministic PPG processing and SQI in `core:ppg`, and optional ML limited to validated signal windows.
- For CameraX work, close every `ImageProxy` exactly once, including error paths.

Keep this file as a compact entry point; put detailed engineering rules in `AGENTS.md` and the linked documents.
