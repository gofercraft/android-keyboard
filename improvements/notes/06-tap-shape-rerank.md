# Re-rank tap suggestions by where the taps landed

Branch `tap-shape-rerank`. Depends on: none.

## Summary
The transformer path ranks tap suggestions mostly by the language model, so a word whose letters sit under the taps can lose to a likelier word that fits them worse. This re-ranks the strip with a tap-fit score and switches only on a clear lead, never to the same word with another case, apostrophe or hyphen (stops its → it's).

## Change
- New `TapShapeReranker.java` (a small logistic model, fitted weights), a hook in `LanguageModelFacilitator.kt`.
- Setting `tap_shape_rerank`, on by default.

## Evidence
- Held-out half of 9,176 cases (4,588): 79.95% → 82.13%, 110 fixed, 10 broken.
- Simulated natural taps 92.96% → 94.60% (+25 / −0); sloppy taps +44 / −5. About 0.1 ms per update.

## Risks
- Correctly typed rare words or names changed to a common word (most of the 10 breaks).

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: Weights fitted on simulated taps over FIT words (MIT) and FUTO's own engine output.
