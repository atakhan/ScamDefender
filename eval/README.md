# Honest eval

Eval measures whether the engine tracks **attack structure over time**, not whether it matches its own phrase bank.

## Tags

| Tag | Meaning |
|-----|---------|
| `easy` | Short scripted cases; may reuse known phrases. Track separately — not the quality gate. |
| `paraphrase` | Same scam scenario, different wording (avoid verbatim `patterns_bank` anchors). |
| `hard_negative` | Legitimate call that shares lexicon with scams (bank fraud alert, police info, etc.). |
| `partial` | Attack stops at early/mid stages — should raise risk but not hit CRITICAL. |
| `noise` | Ambient / irrelevant speech — must stay low risk. |
| `ambiguous` | Grey zone; optional; do not fail only for landing on SUSPICIOUS. |
| `heldout` | Written after lexicon tuning; must not be patched by copying phrases into the bank. |

## Pass rules

| Kind | Pass when |
|------|-----------|
| Scam (`isScam: true`) | `expectedMinRisk ≤ finalRisk` and, if set, `finalRisk ≤ expectedMaxRisk` |
| Non-scam | `finalRisk ≤ expectedMaxRisk` (default `MONITORING` if omitted) |

Scenario mismatch is recorded in `notes` only — it does **not** flip `passed` in v1.

False positive (report metric): non-scam with `finalRisk ≥ SUSPICIOUS`.

## Quality gate (use these, not overall %)

1. `byTag.easy.passRate` — should stay high (sanity).
2. `byTag.paraphrase.passRate` — main scam generalization signal.
3. `hardNegativeFpRate` — main safety signal.
4. Overall `passRate` alone is misleading.

## Baseline

After changing cases or pass logic, run:

```bash
./gradlew :demo:run --args="--eval --output eval/baselines/honest_v1.json"
```

Commit the JSON under `eval/baselines/` when the corpus or scoring rules change.

## Case authoring rules

- Do **not** mark a case `easy` if it is the only evidence a scenario works.
- `paraphrase`: no copy-paste of high-weight phrases from `patterns_bank.json`.
- `hard_negative`: include authority/problem language; steer the **action** toward legitimate channels (app, 900, office visit); no isolation + SMS-code chain.
- `partial`: end before action/isolation climax; set both `expectedMinRisk` and `expectedMaxRisk`.
