package org.futo.inputmethod.latin

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting

/**
 * revise the previous swiped word once the next word is known ("to much" -> "too
 * much"). Only a swiped word the keyboard chose itself (not one picked from the strip) is revised,
 * only among SwipeShapeReranker's top 4 for that swipe, and only while the text since it is exactly
 * "word, one space, next word" with the cursor where it was. The change flashes like an
 * autocorrection, and the next backspace undoes it.
 *
 * Offline with FUTO's own LM natives, swipe-5, the keyboard's own next word: with the guards below
 * 31 revisions, 25 fixed, 1 right word changed.
 */
val PreviousSwipeRevisionSetting = SettingsKey(
    booleanPreferencesKey("previous_swipe_revision"),
    false
)

object PreviousSwipeRevision {
    /** Characters of text before the swiped word handed to the LM as context. */
    const val CONTEXT_CHARS = 200

    /** Revise only when SwipeShapeReranker's top two words for that swipe were within this margin. */
    const val MAX_SHAPE_MARGIN = 6.0

    /**
     * Precision guards: revise only short words (both at most
     * 4 letters) and only when the revision wins by MIN_SCORE_LEAD. On swipe-5 with the keyboard's
     * own next word that gives 31 revisions, 80.6% of them fixes (25 fixed, 1 right word changed).
     */
    const val MAX_WORD_LENGTH = 4
    const val MIN_SCORE_LEAD = 2.0

    /** Weights of ln p(word | previous text) and ln p(next word | previous text + word), fitted on FIT. */
    const val WORD_WEIGHT = 0.2
    const val NEXT_WEIGHT = 0.6

    /** A swiped word waiting for the next word. */
    class Swiped(
        val word: String,
        val candidates: List<String>,
        val shapeScores: List<Double>,
        val textBefore: String,
        val cursor: Int
    ) {
        /**
         * The revision request once [next] has been committed, or null if the text no longer reads
         * "word, one space, next" right after the swiped word.
         */
        fun requestFor(next: String, text: String, cursorNow: Int): Request? {
            val tail = " $next"
            if (next.isEmpty() || !text.endsWith(word + tail)) return null
            if (cursorNow != cursor + tail.length) return null
            val before = text.substring(0, text.length - (word + tail).length)
            if (!textBefore.endsWith(before.takeLast(minOf(before.length, textBefore.length)))) return null
            return Request(word, candidates, shapeScores, textBefore, next, tail, cursorNow)
        }
    }

    class Request(
        val original: String,
        val candidates: List<String>,
        val shapeScores: List<Double>,
        val textBefore: String,
        val next: String,
        val tail: String,
        val cursor: Int
    ) {
        /** The LM context: the current line before the swiped word. */
        val context: String get() = textBefore.substring(textBefore.lastIndexOf('\n') + 1).trim()
    }

    /** A revision that was applied, kept for undo on backspace. */
    class Revision(val original: String, val revised: String, val after: String, val cursor: Int)

    /** Set by the IME: computes a revision off the main thread and applies it if still valid. */
    @Volatile @JvmStatic var engine: ((Request) -> Unit)? = null

    @JvmStatic fun isEnabled(context: Context): Boolean = context.getSetting(PreviousSwipeRevisionSetting)

    /**
     * Remember a swiped word just committed by the keyboard, with SwipeShapeReranker's candidates for it.
     * [text] is the text before the cursor, ending with [word].
     */
    @JvmStatic fun swiped(word: String, text: String, cursor: Int): Swiped? {
        val (words, scores) = LastSwipeShapeScores.forRevision() ?: return null
        if (words.size < 2 || words.none { it.equals(word, ignoreCase = true) }) return null
        if (!text.endsWith(word)) return null
        return Swiped(word, words, scores, text.substring(0, text.length - word.length), cursor)
    }

    @JvmStatic fun submit(request: Request) { engine?.invoke(request) }

    /** Same word with another case, apostrophe or hyphen. */
    fun sameBareWord(a: String, b: String): Boolean {
        val bare = { w: String -> w.lowercase().replace("'", "").replace("-", "") }
        return bare(a) == bare(b)
    }
}
