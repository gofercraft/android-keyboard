# Learn a word the first time it is kept on purpose (off by default)

Branch `learn-kept-words`. Depends on: 11.

## Summary
FUTO learns a new word at its second commit. With this on, a deliberate keep (picking the typed word from the strip, undoing an autocorrection) counts from the first.

## Change
- New `LearnKeptWords.kt`.
- Setting `learn_kept_words`, off by default.

## Evidence
- 53 new words: a word picked from the strip works from its first use (prefix 53, typo 53, against 18 / 12).
- Simulated week: 0 wrong words learned; small overall gain.

## Risks
- Learning a typo kept once on purpose.

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: No data.
