# Backspace after an autocorrection deletes a letter

Branch `backspace-deletes-letter`. Depends on: none.

## Summary
After an autocorrection, backspace currently restores the typed word. This flips the default so backspace deletes one character, as most phone keyboards do. The existing toggle stays.

## Change
- `SettingsValues.java`, `Typing.kt`: default of "Backspace undoes autocorrect" changed to off.

## Evidence
- On screen: 17 of 20 backspace cases end with the word the user meant, against 0 of 20 before. Engine: 14 of 20, 0 broken.

## Risks
- This reverses a long-standing AOSP default that some users rely on. Offered so FUTO can decide; the toggle is unchanged.

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: No data.
