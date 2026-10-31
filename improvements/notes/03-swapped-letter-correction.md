# Autocorrect swapped letters (teh, recieve)

Branch `swapped-letter-correction`. Depends on: none.

## Summary
When FUTO would not autocorrect an unknown typed word, this tries every swap of two adjacent letters, in lower case and capitalised, and autocorrects to the most frequent dictionary word at frequency 100 or more. A typed capital is kept.

## Change
- `LanguageModelFacilitator.kt`, `Suggest.java`, `DictionaryFacilitator(Impl).java`.
- Setting `swapped_letter_correction`, on by default; toggle under Typing.

## Evidence
- 3,205 tap cases: 195 fixed, 0 broken. Swapped-letter typos fixed 99 → 164 of 196; typos before a comma or period 168 → 298 of 370.
- On screen: 38 swapped-letter names and words, 15 → 32 corrected, 0 broken.

## Risks
- A rare word one swap away from a common one; none seen in the sets above.

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: No data.
