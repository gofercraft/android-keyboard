# Fix the previous typed word from context (off by default)

Branch `previous-tap-revision`. Depends on: 17.

## Summary
The largest remaining tap error is a real word typed for another ("if" for "of", "go" for "to"). Once the next word is typed, the tapped word's top 4 are scored by tap fit + 0.5 ln p(word | before) + 1.0 ln p(next | before, word); a dictionary word is replaced only on a lead of 6. One backspace undoes.

## Change
- New `TapRevision.kt`.
- Setting `previous_tap_revision`, off by default.

## Evidence
- 5,274 simulated words: 29 fixed, 3 broken (83% of changes are fixes). No measurable delay (+0.8 ms mean per key).

## Risks
- Only simulated taps so far; it changes a word already on screen.

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: No data.
