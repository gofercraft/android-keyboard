# Swapped-letter fixes into iPhone, YouTube, eBay

Branch `swapped-letter-casing`. Depends on: 03 (via stack-base) and 16.

## Summary
Extends the swapped-letter correction: if no plain swap wins, try the dictionary's own inner-capital spellings (one or two capitals) at a lower bar of 60, and keep that casing after Shift (iPhone, not IPhone).

## Change
- `LanguageModelFacilitator.kt`, `getWordFrequencyIgnoringCase` in the dictionary facilitator. Uses the swapped-letter setting.

## Evidence
- Inner-capital typos 157 → 202 of 236, 0 broken; 0 changes on 3,205 tap cases and 20,000 random typos.
- On screen: names set 36 of 38 (was 32).

## Risks
- 14 swaps pick another inner-capital word (McLean for McLane).

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: No data.
