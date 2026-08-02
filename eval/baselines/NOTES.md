# Baseline honest_v2

After signal/sequence fixes (FeatureExtractor rewrite, soft patterns, phrase-bank synonyms, FSM sequence wiring, early-only risk cap, action-negation suppression).

## Summary

| Metric | v1 | v2 |
|--------|----|----|
| Overall | 22/27 (81%) | see latest JSON |
| paraphrase (tuned set) | 0/5 | 5/5 |
| paraphrase heldout | — | **fail (SAFE)** |
| hard_negative FP | 0% | 0% |
| easy | 10/10 | 10/10 |

## What changed in the detector

- Feature scores saturate on hits (no dict-size dilution); noisy tokens removed; action negation damping
- Soft patterns from features feed FSM/sequence
- `sequenceProgression` from PatternFsm (was `patterns.size/5`)
- Early authority/problem without urgency/isolation/action capped below SUSPICIOUS
- Keyword ACTION suppressed when segment negates code/payment requests
- Expanded `patterns_bank` synonyms (not only easy scripts)

## Reading

Tuned paraphrases recovered, but `bank_scam_paraphrase_heldout_01` stays SAFE — generalization is still incomplete. Gate on `heldout` / new paraphrases, not overall %.
