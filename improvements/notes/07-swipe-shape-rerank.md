# Re-rank swipe candidates by gesture shape (English)

Branch `swipe-shape-rerank`. Depends on: none.

## Summary
The decoder finds the right word but sometimes ranks it second. This asks for its top 16 (beam unchanged) and rescoring each as 0.64 × decoder score plus 32 shape features: path length against the word's key path, start and end distances, headings, corners and slowdowns. It replaces the first word only on a lead of 0.5. It runs only for English with FUTO's English decoder: on the generic path it lost in every language tried (German −1.3 to −3.0 points, Spanish −2.7, French −0.8 to −1.5).

## Change
- New `SwipeShapeReranker.java` (fitted weights), hooks in `SwipeDecoderDictionary.kt`.
- Settings `swipe_shape_rerank` (on) and `swipe_shape_strict` (on, lead 0.5).

## Evidence
- 23,355 held-out real swipes: 90.25% → 93.07% (strict lead: +735 / −74). 24,559 later swipes: 91.02% → 92.67%. Fastest 3%: 84.0% → 89.1%.
- About 0.3 ms per swipe.

## Risks
- 74 right swipes become wrong at the strict lead (has → had, them → then). A retune on fresh data would help.

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: Weights fitted on swipe.futo.org traces (MIT / CC0, FIT set only; the test sets were never used for fitting).
