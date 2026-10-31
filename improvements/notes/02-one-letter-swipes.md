# Short swipes on a and i give "a" and "I" (English)

Branch `one-letter-swipes`. Depends on: none.

## Summary
FUTO's swipe decoder never returns a one-letter word. A single-finger gesture whose path is shorter than 0.5 key widths, centred on "a" or "i", now puts "a" or "I" first. English only: in French the same stroke means "à" as often as "a" (8 fixed, 33 broken), and German has no one-letter words.

## Change
- `SwipeDecoderDictionary.kt`: a short-gesture check, `ONE_LETTER_LANGUAGES = setOf("en")`.
- Setting `one_letter_swipe`, on by default; toggle under Swipe.

## Evidence
- 23,355 held-out real swipes (swipe.futo.org): top-1 90.25% → 92.50%, 531 fixed, 6 broken.
- One-letter words: 3 → 534 of 606. Taps unchanged.

## Risks
- 5 short swipes meant as "in" or "it" became "I", and 1 "As" became "a".

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: The 0.5 threshold was tuned on FUTO's FIT swipes (MIT).
