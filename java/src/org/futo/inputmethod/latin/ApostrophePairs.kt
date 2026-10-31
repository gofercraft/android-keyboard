package org.futo.inputmethod.latin

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting

/**
 * words that are both a plain word and a contraction without its apostrophe
 * ("well" / "we'll", "its" / "it's"). FUTO keeps whichever form its dictionary prefers, nearly
 * always the plain one for well, were, wed, shed, shell and hell, and the contraction for wont,
 * ill and lets. After the commit the transformer LM compares both forms from the text before, and
 * again once the next word is known; a clear win replaces the word (backspace undoes it, as for
 * PreviousSwipeRevision).
 *
 * FUTO's real output in sentence context (1,089 typed pair words): right forms
 * go from 864 to 971 (107 fixed), no right form changed.
 */
val ApostrophePairsSetting = SettingsKey(
    booleanPreferencesKey("apostrophe_pairs"),
    true
)

object ApostrophePairs {
    /** Plain word to contraction. "id" is left out: FUTO already gets it right, and "ID" is a third reading. */
    @JvmField val PAIRS = mapOf(
        "ill" to "I'll", "were" to "we're", "well" to "we'll", "hell" to "he'll", "shell" to "she'll",
        "its" to "it's", "lets" to "let's", "cant" to "can't", "wont" to "won't", "wed" to "we'd",
        "shed" to "she'd"
    )

    /** Replace at commit when the other form leads by this, from the text before alone. */
    const val AUTO_MIN_LEAD = 3.0

    /** Replace once the next word is known when the other form leads by this, next word included. */
    const val NEXT_MIN_LEAD = 5.0

    @JvmStatic fun isEnabled(context: Context): Boolean = context.getSetting(ApostrophePairsSetting)

    /** The other form of [word] as the dictionary spells it, or null if [word] is not a pair word. */
    @JvmStatic fun otherForm(word: String): String? {
        val bare = word.lowercase().replace("'", "")
        val contraction = PAIRS[bare] ?: return null
        return if (word.contains('\'')) bare else contraction
    }

    /** [form] cased for its place: a sentence start gets a capital, and "I'll" keeps its own. */
    @JvmStatic fun cased(form: String, context: String): String {
        if (form.startsWith("I'")) return form
        val start = context.isEmpty() || context.last() in ".!?"
        return if (start) form.replaceFirstChar { it.titlecase() } else form
    }

    /** One check: [request].candidates holds the committed word and its other form; [withNext] uses the next word. */
    class Check(val request: PreviousSwipeRevision.Request, val withNext: Boolean)

    /** Set by the IME: scores a check off the main thread and applies it if still valid. */
    @Volatile @JvmStatic var engine: ((Check) -> Unit)? = null
    @JvmStatic fun submit(check: Check) { engine?.invoke(check) }

    /** The pair word waiting for the next word, unless it was already replaced at commit. */
    @Volatile @JvmStatic var waiting: PreviousSwipeRevision.Swiped? = null

    /** The checks still running, at commit and with the next word. */
    @Volatile var pendingNow: kotlinx.coroutines.Job? = null
    @Volatile var pendingNext: kotlinx.coroutines.Job? = null
    @JvmStatic fun cancelPending() {
        pendingNow?.cancel(); pendingNow = null
        pendingNext?.cancel(); pendingNext = null
    }
}
