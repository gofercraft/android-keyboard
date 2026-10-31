# Keep words the user has taught ahead of close dictionary words

Branch `taught-word-boost`. Depends on: 12.

## Summary
A word the user committed twice that the dictionary lacks is kept when typed exactly, and preferred over a close dictionary word within a margin on taps and swipes (Niamh not Miami).

## Change
- New `TaughtWords.kt`, hooks in the dictionary facilitator and swipe decoder.
- Setting `taught_word_boost`, on by default.

## Evidence
- Simulated weeks: +5 to +8 tap uses right per arm, 0 broken; strip promotion +1 to 2, 0 broken.

## Risks
- An ordinary word pulled to a taught one; watched for, none seen.

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: No data.
