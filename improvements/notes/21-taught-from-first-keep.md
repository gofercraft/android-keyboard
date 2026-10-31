# Teach a word kept on purpose at once; Delete learned words clears both lists

Branch `taught-from-first-keep`. Depends on: 20.

## Summary
A word kept on purpose (picked from the strip, an undone autocorrection, a retype) is taught from that first keep rather than the second. Also: "Delete learned words" now clears this list and the retyped-word list from branch 16.

## Change
- `TaughtWords.kt` (list of up to 300 in `taught_kept_words`), `GeneralIME.kt`, `KeepRetypedWord.kt`, `DictionaryFacilitatorImpl.clearUserHistoryDictionary`.
- Uses the taught-word setting.

## Evidence
- Simulated week with this rule: new-word uses right 77 → 92 and 75 → 94 of 170 on two seeds, 0 bad picks.

## Risks
- The lists sit in the settings datastore, so they are included in "Export configuration". Nothing leaves the phone.

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: No data.
