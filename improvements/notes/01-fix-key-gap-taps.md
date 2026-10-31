# Don't drop taps that land in the 1 px gap between key rows

Branch `fix-key-gap-taps`. Depends on: none.

## Summary
Hitboxes come from rounded row positions, so on some screen sizes a 1 px strip between two rows belongs to no key. A tap on that strip does nothing, and the word loses a letter ("home" becomes "hoe", then "how"). With this change, such a tap goes to the nearest key whose hitbox is within max(2 px, key height / 8). Taps inside a hitbox are unchanged.

## Change
- `KeyDetector.java`: a fallback to the nearest close key; new package-visible `distanceToHitBox(Rect, x, y)`.
- New `KeyDetectorGapTests` (4 cases). It runs under Robolectric as a JVM unit test (`./gradlew testUnstableDebugUnitTest`, 4 run, 0 failed). The repo's tests are instrumented-only, so build.gradle gains two test-only items: the Robolectric 4.16.1 test dependency and `unitTests.returnDefaultValues = true`.
- No setting: the change only affects taps that currently do nothing.

## Evidence
- 1,000 FIT tap words with simulated touch noise: in 3 of them a tap was dropped on a gap pixel (home, sister, pharmaceutical).
- 3,205 tap cases in FUTO's engine: +2 fixed, 0 broken, no other output changed.

## Risks
- Very low. A tap that used to be dropped now goes to a key a few pixels away.

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: Bug fix, no data.
