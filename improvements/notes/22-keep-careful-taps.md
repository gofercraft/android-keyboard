# Keep slow, careful taps as typed (off by default)

Branch `keep-careful-taps`. Depends on: 21.

## Summary
When space would autocorrect a tapped word, keep it as typed if every gap between taps was at least 400 ms and every tap was within 0.4 key pitch of its key's centre. Swipes and words without tap positions never fire.

## Change
- New `KeepCarefulTaps.kt`, tap times recorded in `InputLogic.java`.
- Setting `keep_careful_taps`, off by default.

## Evidence
- Google TSI real taps (43,735 taps, 16 people; measurement only): 2 of 1,544 real typos would be wrongly kept; 26 of 589 deliberate random strings kept right.

## Risks
- A slow word aimed one key off is kept as nonsense ("ead" for "was"). It goes beyond what other keyboards do, so it is opt-in.

## Provenance and licence
- Written with an AI assistant (Claude) and reviewed by gofercraft.
- The evidence comes from FUTO's own engine (its shipped native libraries, decoder, dictionary and model) replayed on a desktop host, and from the built app on an emulator. Gboard was used only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into the code, the data or the fitted constants.
- Data: Measured on the Google TSI dataset (CC BY 4.0), which is not shipped or trained on.
