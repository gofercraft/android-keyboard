# Keep a word the user retypes after deleting its autocorrection

Branch `keep-retyped-word`. Depends on: 15.

## Summary
If the user deletes an autocorrection and types their own spelling again, keep it, and leave that exact spelling alone from then on (a list of up to 200, on the phone only). Near-misses of it are still corrected.

## Change
- New `KeepRetypedWord.kt`, hooks in `InputLogic.java`.
- Setting `keep_retyped_word`, on by default; list in `kept_retyped_words`.

## Evidence
- Kept spelling and its next plain uses: 16 of 16, against 0 of 16. Other typos after a deleted correction still corrected (11 of 11).
- On screen: 7 of 7 kept and right on the next use; typo control 6 of 6 still corrected.

## Risks
- A typo retyped exactly after deleting its correction is kept. The list sits in the settings datastore, so it is included in "Export configuration". "Delete learned words" clears it (branch 21).

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: No data.
