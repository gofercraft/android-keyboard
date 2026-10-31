# Split a word joined by a missed space

Branch `missed-space-split`. Depends on: 10.

## Summary
A typed non-word that splits into two common dictionary words ("inan", "inban") is split when the language model prefers the split over the best single-word correction.

## Change
- New `SpaceSplit.kt`.
- Setting `missed_space_split`, on by default.

## Evidence
- 450 missed and space-row cases in context: 261 → 364 right, 0 right words changed; all 58 offered chips right.

## Risks
- A kept typo split into two words ("a bums"); backspace undoes.

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: No data.
