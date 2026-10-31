# Swipe words you have typed before

Branch `history-swipe-weight`. Depends on: 14.

## Summary
Taught words are often missing from the swipe decoder's candidates, so ranking can't help. This gives the user-history dictionary 1.5 times the weight in the swipe decoder.

## Change
- `TaughtWords.kt`, `DictionaryFacilitatorImpl.java`.
- Setting `history_swipe_weight`, on by default.

## Evidence
- Simulated week, real decoder: swiped new words 18 → 32 of 65; ordinary swipes 869 → 873 of 954.

## Risks
- At weight 2 it breaks ordinary swipes (for → four); 1.5 broke 1 in a prefilled run.

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: No data.
