package org.futo.inputmethod.latin

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.uix.DataStoreHelper
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting

/**
 * keep words the user has taught the keyboard (names, places, slang) ahead of close
 * dictionary words. A word is taught when it is in user history, is not a main-dictionary word,
 * and has been committed at least [MIN_KEPT] times. In a simulated week
 * FUTO learned such words by their 2nd use but then lost them to close dictionary words: "Niamh"
 * tapped became "Miami", a swiped "Saoirse" became "side".
 */
val TaughtWordBoostSetting = SettingsKey(
    booleanPreferencesKey("taught_word_boost"),
    true
)

/** Words the user kept on purpose (TaughtWords.rememberKept), newest last, one per line, as written. */
val TaughtKeptWordsSetting = SettingsKey(
    stringPreferencesKey("taught_kept_words"),
    ""
)

/** Swipes: weight the user-history trie above the main dictionary (TaughtWords.HISTORY_TRIE_WEIGHT). */
val HistorySwipeWeightSetting = SettingsKey(
    booleanPreferencesKey("history_swipe_weight"),
    true
)

object TaughtWords {
    /** User-history commits before a word counts as taught. */
    const val MIN_KEPT = 2

    /**
     * A word the user kept on purpose (picked as typed from the strip, an undone autocorrection, or
     * a retype after deleting one: FUTO's importance-3 commits) is taught from that first keep. An
     * ordinary commit cannot be told apart from an unnoticed typo, so those still need [MIN_KEPT].
     * Week harness, arm C, "seen once" rule live: new-word
     * uses right 77 -> 99 and 75 -> 96 of 170 on two seeds with 0 bad picks when the cache held only
     * words the person meant; with unnoticed typos cached too, 4 bad picks. This keep-or-2 rule is
     * the deployable version of that guard.
     */
    private const val MAX_REMEMBERED = 300
    private var kept: MutableList<String>? = null

    @Synchronized private fun keptList(): MutableList<String> = kept ?: DataStoreHelper.getSetting(TaughtKeptWordsSetting)
        .split('\n').filter { it.isNotEmpty() }.toMutableList().also { kept = it }

    @Synchronized @JvmStatic fun isKept(word: String): Boolean = word.isNotEmpty() && keptList().contains(word)

    @OptIn(DelicateCoroutinesApi::class)
    @Synchronized @JvmStatic fun rememberKept(context: Context, word: String) {
        if (word.isEmpty() || word.contains('\n') || !isEnabled(context)) return
        val list = keptList()
        list.remove(word)
        list.add(word)
        while (list.size > MAX_REMEMBERED) list.removeAt(0)
        val joined = list.joinToString("\n")
        GlobalScope.launch { context.setSetting(TaughtKeptWordsSetting.key, joined) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Synchronized @JvmStatic fun forgetKept(context: Context) {
        kept = mutableListOf()
        GlobalScope.launch { context.setSetting(TaughtKeptWordsSetting.key, "") }
    }

    /**
     * Taps: promote when ln(top dictionary score / taught word's score) is at most this. Fitted on
     * the engine's v2 week strips: the two taught words in the strip
     * behind a correction ("Standard" for Stansted, ln 1.3; "Niamg" for Niamh, ln 2.2), and no
     * right word changed in any arm.
     */
    const val TAP_MARGIN = 2.5

    /** Taps: only for typed words of at least this many letters. */
    const val MIN_TYPED_LENGTH = 3

    /** Swipes: promote when the taught word's SwipeShapeReranker score is within this of the first word's. */
    const val SWIPE_MARGIN = 2.0

    /**
     * Swipes: the swipe decoder's weight for the user-history trie, times the locale's weight (1 for
     * the main language). Engine week sweep on FUTO's neural decoder:
     * at 1.5 swiped new words go from 18 to 32 of 65 and ordinary swipes from 869 to 873 of 954; at 2
     * ordinary swipes fall to 836 (words used earlier steal swipes: for -> four, that -> what).
     */
    const val HISTORY_TRIE_WEIGHT = 1.5f

    @JvmStatic fun isHistorySwipeWeightEnabled(context: Context): Boolean =
        context.getSetting(HistorySwipeWeightSetting)

    /** Set by the dictionary facilitator: whether a word is taught. */
    @Volatile @JvmStatic var checker: java.util.function.Predicate<String>? = null

    @JvmStatic fun isTaught(word: String): Boolean = checker?.test(word) == true

    @JvmStatic fun isEnabled(context: Context): Boolean = context.getSetting(TaughtWordBoostSetting)

    /**
     * Taps: the typed word is itself taught, so autocorrection keeps it ("Niamh" not "Miami"). Week
     * strips: 5 to 8 fixes per arm, none broken.
     */
    @JvmStatic fun keepTyped(typed: String?): Boolean =
        typed != null && typed.length >= MIN_TYPED_LENGTH && isTaught(typed)

    /**
     * Swipes, after SwipeShapeReranker's re-rank: [order] with a taught word moved first if it is within
     * [SWIPE_MARGIN] of the first word ([scores] are 08's, in input order), or null to keep it.
     */
    @JvmStatic fun promoteSwipe(order: IntArray, words: List<String>, scores: DoubleArray): IntArray? {
        if (order.size < 2) return null
        val top = scores[order[0]]
        if (isTaught(words[order[0]])) return null
        val k = order.drop(1).firstOrNull { scores[it] >= top - SWIPE_MARGIN && isTaught(words[it]) } ?: return null
        return intArrayOf(k) + order.filter { it != k }
    }

    /**
     * Taps: the index in [words] of a taught word to autocorrect to, or -1. [fromUserHistory] marks
     * the user-history results; [top] is the index of the best dictionary result.
     */
    @JvmStatic fun promoteTap(typed: String, typedIsMainWord: Boolean, words: List<String>, scores: IntArray,
                              fromUserHistory: BooleanArray, top: Int): Int {
        if (typed.length < MIN_TYPED_LENGTH || typedIsMainWord || top < 0 || scores[top] <= 0) return -1
        var best = -1
        for (i in words.indices) {
            if (!fromUserHistory[i] || scores[i] <= 0 || words[i].equals(typed, ignoreCase = true)) continue
            if (words[i].isEmpty() || !words[i][0].equals(typed[0], ignoreCase = true)) continue
            if (best >= 0 && scores[i] <= scores[best]) continue
            if (!isTaught(words[i])) continue
            best = i
        }
        if (best < 0 || best == top) return -1
        return if (kotlin.math.ln(scores[top].toDouble() / scores[best]) <= TAP_MARGIN) best else -1
    }
}
