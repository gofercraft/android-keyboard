package org.futo.inputmethod.latin

import androidx.datastore.preferences.core.booleanPreferencesKey
import org.futo.inputmethod.latin.uix.SettingsKey

/**
 * once the next word is known, offer a one-tap fix for the previous word in the
 * suggestion strip ("to → too") when another of its candidates fits nearly as well or better.
 * Nothing changes unless the chip is tapped. Works for swiped words (SwipeShapeReranker's candidates) and
 * typed words (TapShapeReranker's candidates). PreviousSwipeRevision's automatic revision stays a separate setting.
 *
 * Offline: taps, simulated noisy
 * taps on swipe-2 sentences (test half): 3.7 offers per 100 words, 51% right, 18% of misses
 * offered. Swipes, swipe-5 with the keyboard's own next word and PreviousSwipeRevision on: no threshold keeps
 * 1% of misses at 60% right (at a lead of 2.5: 16 offers, 69% right, 11 misses), so offers for
 * swiped words are a separate setting, off by default.
 */
val PreviousWordOfferSetting = SettingsKey(
    booleanPreferencesKey("previous_word_offer"),
    true
)

/** Also offer fixes for swiped words. Off by default (see above). */
val PreviousWordOfferSwipedSetting = SettingsKey(
    booleanPreferencesKey("previous_word_offer_swiped"),
    false
)

object PreviousWordOffer {
    /** Offer when the alternative scores at least this far from the word on screen (it may be lower). */
    const val SWIPE_OFFER_MIN_LEAD = 2.5
    const val TAP_OFFER_MIN_LEAD = 0.0

    /**
     * Typed words whose TapShapeReranker lead over the 2nd word is at least this are left alone. Sim-taps:
     * the decision then runs for 29% of typed words and keeps 128 of 135 offered fixes
     *.
     */
    const val TAP_MAX_SHAPE_MARGIN = 3.0

    /** Candidates besides the word on screen that get p(next | ...): 3 of 4 lose 1 swipe and 3 tap offers. */
    const val NEXT_SCORED_OTHERS = 2

    /** LM weights for typed words on TapShapeReranker's score scale, fitted on the tune half of sim-taps. */
    const val TAP_WORD_WEIGHT = 0.3
    const val TAP_NEXT_WEIGHT = 0.5

    /** The chip text: "original → fix". */
    @JvmStatic fun label(original: String, fix: String): String = "$original \u2192 $fix"
}

/** The outcome for the previous word: [word] replaces it now ([auto]) or is offered as a chip. */
class PreviousWordDecision(val word: String, val auto: Boolean)

/** TapShapeReranker's top 4 for the last tap strip, with their scores, kept for PreviousWordOffer. */
object LastTapShapeScores {
    @Volatile private var last: Pair<List<String>, List<Double>>? = null
    fun set(words: List<String>, scores: List<Double>) { last = words to scores }
    fun take(): Pair<List<String>, List<Double>>? = last.also { last = null }
}
