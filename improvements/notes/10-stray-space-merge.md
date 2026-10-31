# Join a word split by a stray space

Branch `stray-space-merge`. Depends on: 09.

## Summary
"atta cked": when two pieces typed with one space join into a dictionary word and are not both words, join them. Single capitals and capitalised second pieces are left alone (initials, "De Witt").

## Change
- New `StraySpaceMerge.kt`, hook in `InputLogic.java`.
- Setting `stray_space_merge`, on by default.

## Evidence
- 132 of 150 stray spaces fixed in context; 1 false join in 37,134 correctly typed pairs (per se). No language model needed.

## Risks
- "per se" style joins; backspace undoes.

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: No data.
