# ScamDefender

Real-time on-device detection of social engineering patterns in reconstructed phone conversations.

## MVP (core engine)

The `:core` module implements the detection pipeline:

```
SpeechSegment → FeatureExtractor → PatternDetector → StageTransitionDetector
                                      ↓ (trigger)
                                 ScenarioModel → RiskAggregator → StateMachine
```

## Requirements

- JDK 17+ (full JDK, not JRE)
- Gradle 8.x (wrapper included)

### Windows: install JDK

If `./gradlew` fails with "JDK 17 not found", install a project-local JDK:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup-jdk.ps1
```

`gradlew.bat` automatically uses `.jdk/` when present.

## ML models (optional, recommended)

```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup-models.ps1
.\gradlew.bat :demo:run --args="--status"
```

Downloads Silero VAD, MiniLM ONNX, Sherpa-ONNX JARs and Russian streaming STT model.

## Build and test

```bash
./gradlew :core:test :demo:build
```

## Demo CLI

Run detection on a mock transcript:

```bash
./gradlew :demo:run --args="--transcript samples/transcripts/bank_scam_01.txt --output report.json"
```

Run eval suite:

```bash
./gradlew :demo:run --args="--eval --output reports/eval.json"
```

## Project structure

| Module | Description |
|--------|-------------|
| `:core` | Detection engine (Kotlin JVM) |
| `:demo` | CLI harness |
| `:android-app` | Android shell stub (post-MVP) |

## Samples

Test transcripts live in `samples/transcripts/`. Eval cases are defined in `eval/manifest.json`.

## Documentation

- [CONCEPT.md](CONCEPT.md) — product vision
- [CORE.md](CORE.md) — detection engine design
- [STACK_SPEC.md](STACK_SPEC.md) — technology stack
- [SCAM_SCENARIOS.md](SCAM_SCENARIOS.md) — attack scenarios and stages
- [STAGES_DETECTION_SYSTEM.md](STAGES_DETECTION_SYSTEM.md) — stage transition detection
