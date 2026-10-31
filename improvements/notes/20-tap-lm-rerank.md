# Use word frequency and context to pick the tap word (off by default)

Branch `tap-lm-rerank`. Depends on: 19.

## Summary
A second stage after the tap re-ranker: re-pick the tap word from tap fit, dictionary frequency and the model's score for the word after the previous words. Replaces only on a lead of 2.0, never across a two-word split.

## Change
- New `TapLmReranker.java` (fitted weights). One extra model call per tap update, with a timeout of 150 ms.
- Setting `tap_lm_rerank`, off by default.

## Evidence
- Cross-validated: simulated taps 101 fixed / 9 broken of 5,998; tap cases 115 / 12. Cost +11.7 ms mean per key (within noise).

## Risks
- Short abbreviations (tv → to, ok → on).

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: Weights fitted on simulated taps; frequencies from FUTO's own dictionary.
