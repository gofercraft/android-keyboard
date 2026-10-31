# Add or drop a missing apostrophe from context (well / we'll)

Branch `apostrophe-pairs`. Depends on: 13.

## Summary
For ill, were, well, hell, shell, its, lets, cant, wont, wed, shed: compare both forms with the model and switch at commit on a lead of 3 (ln p), or after the next word on a lead of 5.

## Change
- New `ApostrophePairs.kt`.
- Setting `apostrophe_pairs`, on by default.

## Evidence
- 1,089 pair cases: 864 → 971 right, 0 broken. Held-out halves: 57 of 111 fixed with 0 broken, 67 of 114 with 2 broken.

## Risks
- Changing a plain word that was meant; backspace undoes.

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: No data.
