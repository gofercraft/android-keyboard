# Swipe names and places missing from the dictionary

Branch `swipe-names`. Depends on: 18.

## Summary
About 25,000 first names, surnames and places, offered to the swipe decoder only at 0.75 of the main dictionary's weight, never to tap suggestions.

## Change
- New `NamesDictionary.kt`, `res/raw/names_en.txt` (sources in its header).
- Setting `names_dictionary`, on by default.

## Evidence
- Real swipes: +54 / −3 and +210 / −4 on two sets, about even on two others. On screen: 9 of 18 name swipes right, against 0.

## Risks
- A swiped common word becomes a name (7 broken on one set). First-load time on a phone not measured.

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: US SSA baby names (public domain, via the CC0 R package babynames), US Census 1990 names (public domain), Natural Earth populated places (public domain). No attribution required.
