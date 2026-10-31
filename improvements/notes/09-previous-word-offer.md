# Offer a one-tap fix for the previous word

Branch `previous-word-offer`. Depends on: 08.

## Summary
Same scoring as the previous-word fix, but shown as a chip ("to → too") at the start of the strip. Nothing changes unless the chip is tapped.

## Change
- New `PreviousWordOffer.kt`, strip hooks.
- Settings `previous_word_offer` (typed words, on) and `previous_word_offer_swiped` (off).

## Evidence
- Typed words: about 51 to 55% of chips are right. Swiped words at lead 2.5: 82% right but rare, so off.
- Decision median 83 ms, p95 210 ms on a desktop core, off the main thread.

## Risks
- New UI in the strip.

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: No data.
