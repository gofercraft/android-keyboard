# Keyboard accuracy improvements for FUTO Keyboard

This fork of FUTO Keyboard (master 31e720dd) holds 22 small changes that make typing and swiping more accurate.
Each change is on its own branch and can become its own pull request. FUTO is welcome to take any of them, in
whole or in part.

## Results

All numbers are top-1 accuracy, measured in FUTO's own engine (its shipped native libraries, decoder, dictionary and
model) replayed on a desktop, or in the built app on an emulator. The test sets were never used for tuning.

| Test | FUTO | With these changes |
| --- | --- | --- |
| Real held-out swipes (swipe.futo.org, 23,355) | 91.36% | 94.54% |
| Fastest 3% of those swipes | 84.0% | 91.4% |
| One-letter swipes (a, I) | 0.5% | 88.1% |
| Swapped-letter typos (196) | 99 | 164 |
| Misspelled names and words on screen (38) | 17 | 36 |
| Stray space inside a word (150, in context) | 0 | 132 |
| Missed space between words (450) | 261 | 364 |

The swipe rows score a word as right when the label differs only by a dropped apostrophe. Rows that involve
off-by-default branches (08, 18, 20, 22) were measured with those branches switched on. None of this has been
checked yet by people typing on real phones.

## Branches

The first seven branches are independent, and each one starts from master. The rest form a stack: each starts from the
one before it, beginning with `stack-base`, which is just the seven standalone branches together. Review and merge in
this order.

| # | Branch | What it does | Default |
| --- | --- | --- | --- |
| 01 | fix-key-gap-taps | Taps in the 1 px gap between rows go to the nearest key (bug fix, with a test) | always |
| 02 | one-letter-swipes | Short swipes on a or i give "a" or "I" (English) | on |
| 03 | swapped-letter-correction | Autocorrect two swapped letters | on |
| 04 | backspace-deletes-letter | Backspace after an autocorrection deletes a letter | on (toggle kept) |
| 05 | everyday-words | idk, btw, nah, TV, selfie and similar words | on |
| 06 | tap-shape-rerank | Pick the tap word partly from where the taps landed | on |
| 07 | swipe-shape-rerank | Re-rank swipe candidates by gesture shape (English) | on |
| 08 | previous-swipe-fix | Fix the previous swiped word from the next one | off |
| 09 | previous-word-offer | One-tap chip to fix the previous word | typed on, swiped off |
| 10 | stray-space-merge | Join "atta cked" | on |
| 11 | missed-space-split | Split "inan" | on |
| 12 | learn-kept-words | A deliberate keep teaches a word at once | off |
| 13 | taught-word-boost | Taught words beat close dictionary words | on |
| 14 | apostrophe-pairs | well / we'll, its / it's from context | on |
| 15 | history-swipe-weight | Swipe words you have typed before | on |
| 16 | keep-retyped-word | Keep a spelling the user retyped after deleting its correction | on |
| 17 | swapped-letter-casing | Swapped letters into iPhone, YouTube, eBay | with 03 |
| 18 | previous-tap-revision | Fix the previous typed word from context | off |
| 19 | swipe-names | About 25,000 names and places for swiping only | on |
| 20 | tap-lm-rerank | Word frequency and context for tap words | off |
| 21 | taught-from-first-keep | Teach a word kept on purpose at once; Delete learned words clears both lists | with 13 |
| 22 | keep-careful-taps | Keep slow, careful taps as typed | off |

Each branch's commit message has its summary. The full notes for each one (change, evidence, risks, data) are in
[`improvements/notes/`](improvements/notes/).

## Every change follows the same rules

- It has its own setting, and its default is chosen from fixed against broken counts on held-out data.
- One backspace undoes any automatic change.
- It never touches a word the user picked from the strip.
- Anything that calls the language model runs off the main thread, with a timeout.
- Learned lists stay on the phone and are cleared by "Delete learned words".

## Licence and provenance

- **Code:** FUTO Keyboard is under the FUTO Source First License 1.1. This fork keeps it unchanged, and the changes
  here are offered under the same terms. FUTO is welcome to relicense them however it needs, including under its CLA.
- **Data shipped:** the everyday-word list (05) was made by hand for this work. The names list (19) comes only from
  public-domain sources: US Social Security Administration baby names (via the CC0 R package babynames), US Census
  1990 names, and Natural Earth populated places.
- **Data used to fit or measure, not shipped:** FUTO's swipe dataset at swipe.futo.org (FIT set for fitting, held-out
  sets for testing), simulated taps, and the Google TSI tap dataset (CC BY 4.0, measurement only, for 22).
- **Gboard** was observed only as a yardstick for behaviour. No Gboard code, model, dictionary or output went into
  any change, data file or fitted constant.
- **How it was made:** the changes were written with an AI assistant (Claude) and reviewed and tested by
  gofercraft. The numbers come from lab runs, not from the assistant's memory.
