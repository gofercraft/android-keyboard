# Add everyday chat words the main wordlist lacks

Branch `everyday-words`. Depends on: none.

## Summary
FUTO's English wordlist misses common chat words (idk, btw, nah, omg, TV, UK, selfie) or has them at almost zero frequency (lol, app). This adds a small read-only supplement dictionary from a text file.

## Change
- `SupplementDictionary.kt`, `res/raw/supplement_en.txt` (113 words), hooks in `DictionaryFacilitatorImpl.java`.
- Setting `supplement_dictionary`, on by default.

## Evidence
- Swipes +7 / −0; taps unchanged. On screen, typed chat words kept 106 of 113 against 92.
- Without it, an A/B regressed DNA → Dan and USB → sub.

## Risks
- Little measured gain in prediction (14 of 113 offered after two letters, against 11).

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: The word list is hand-made for this change and offered under FUTO's terms.
