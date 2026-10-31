package org.futo.inputmethod.latin

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting

/**
 * a missed space ("inan") or a space-row slip ("inban", "innan"). When a typed
 * non-word splits into two dictionary words, the split and the committed word are scored with the
 * transformer LM after the commit: S = ln p(w1 | before) + ln p(w2 | before w1). A clear win
 * replaces the committed word (backspace undoes it, as for PreviousSwipeRevision); a near win is offered as
 * PreviousWordOffer's chip.
 *
 * FUTO's real output in sentence context (450 cases with
 * TapShapeReranker): right words go from 261 to 364 (103 automatic fixes), no right word changed, and all
 * 58 offers right. Tap-scale without context: 72 fixes, 2 kept typos split, 0 right words changed.
 */
val SpaceSplitSetting = SettingsKey(
    booleanPreferencesKey("missed_space_split"),
    true
)

object SpaceSplit {
    /** Both pieces need at least this dictionary frequency (0-255); single letters only "a", "i". */
    const val MIN_PIECE_FREQUENCY = 60

    /** Keys just above the space bar, hit instead of it. */
    const val SLIP_KEYS = "cvbnm"

    /**
     * Replace an autocorrected word when the best split scores at least this far from it. With
     * sentence context (space15 host run, all09): 14 more fixes than at a lead of 2, while 5 of
     * 3,000 single-edit typos FUTO had corrected would be split. Without context (tap-scale) a lead
     * of -2 broke 4 right corrections for 7 fixes, hence not lower.
     */
    const val AUTO_MIN_LEAD = -1.0

    /**
     * The typed non-word was kept: replace it when the best split scores at least this far from the
     * best dictionary word in the strip (it may be lower). Tap-scale: 38 of 120 missed spaces fixed,
     * 1 of 17 kept typos split.
     */
    const val KEPT_AUTO_MIN_LEAD = -2.0

    /** Offer the split as a chip from this lead. */
    const val OFFER_MIN_LEAD = -6.0

    /** The keyboard already committed another split: replace only on a clear win. */
    const val RESPLIT_MIN_LEAD = 4.0

    @JvmStatic fun isEnabled(context: Context): Boolean = context.getSetting(SpaceSplitSetting)

    /**
     * A word to check. [committedIsWord]: the committed word is a dictionary word (an
     * autocorrection), and the split has to beat it on the LM. Otherwise the typed non-word was
     * kept, which the LM cannot judge (its first token is often a word: "a|young"); the split then
     * has to beat [rival], the strip's best dictionary word ("abums": "albums", not "a bums"), or
     * wins outright when there is none. [autoAllowed]: autocorrection is on; when off, splits are
     * only offered.
     */
    class Split(
        val request: PreviousSwipeRevision.Request,
        val committedIsWord: Boolean,
        val rival: String?,
        val autoAllowed: Boolean
    )

    /** Set by the IME: scores a split off the main thread and applies or offers it if still valid. */
    @Volatile @JvmStatic var engine: ((Split) -> Unit)? = null
    @JvmStatic fun submit(split: Split) { engine?.invoke(split) }

    /** The decision still running for the last committed word, if any. */
    @Volatile var pending: kotlinx.coroutines.Job? = null
    @JvmStatic fun cancelPending() { pending?.cancel(); pending = null }

    /**
     * Two-word readings of [typed] ("in an"), lower case, or empty if [typed] is a word itself.
     * [isWord] and [frequency] answer for one word as the dictionary spells it.
     */
    @JvmStatic
    fun candidates(typed: String, isWord: java.util.function.Predicate<String>,
                   frequency: java.util.function.ToIntFunction<String>): List<String> {
        val t = typed.lowercase()
        if (t.length < 3 || !t.all { it in 'a'..'z' }) return emptyList()
        val cap = { w: String -> w.replaceFirstChar { it.titlecase() } }
        if (isWord.test(typed) || isWord.test(t) || isWord.test(cap(t))) return emptyList()
        val ok = { w: String ->
            (w.length > 1 || w == "a" || w == "i") && (isWord.test(w) || isWord.test(cap(w))) &&
                    maxOf(frequency.applyAsInt(w), frequency.applyAsInt(cap(w))) >= MIN_PIECE_FREQUENCY
        }
        val out = LinkedHashSet<String>()
        for (i in 1 until t.length) {
            val head = t.substring(0, i)
            if (!ok(head)) continue
            if (ok(t.substring(i))) out.add(head + " " + t.substring(i))
            if (t[i] in SLIP_KEYS && i + 1 < t.length && ok(t.substring(i + 1))) out.add(head + " " + t.substring(i + 1))
        }
        return out.toList()
    }
}
