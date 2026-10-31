# Fix the previous swiped word once the next word is known (off by default)

Branch `previous-swipe-fix`. Depends on: 07 (swipe-shape-rerank), via stack-base.

## Summary
Some swipe errors only become clear from the next word ("to much" → "too much"). After the next word is committed, the previous swipe's top 4 are rescored with the language model, and a short word is replaced on a clear lead if the text is untouched. One backspace undoes. Also includes the language-model swipe check it relies on (`transformer_swipe_rescore`), off by default.

## Change
- New `PreviousSwipeRevision.kt`; model calls in `LanguageModel.kt` / `LanguageModelFacilitator.kt`; `GeneralIME.kt`, `InputLogic.java`.
- Settings `previous_swipe_revision` and `transformer_swipe_rescore`, both off by default.

## Evidence
- swipe-5 (24,559): 36 fixed, 2 broken; about 80% of visible changes are fixes (to → too, or → our, of → off).
- The swipe check alone: +0.3 to +0.4 points on the host, but on screen without sentence context it lost (swipe-2 400: 8 fixed, 21 broken), so it stays off.

## Risks
- A word already on screen changes by itself; about 1 in 5 such changes is wrong.

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: No data.
