package org.futo.inputmethod.latin

import androidx.datastore.preferences.core.booleanPreferencesKey
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting

/**
 * a stray space inside a word ("atta cked"). When the second piece is committed and
 * the two pieces as typed join into a dictionary word while at least one piece is not a word, the
 * two committed words are replaced by the joined word. Backspace straight after puts them back.
 *
 * Offline on FUTO 0.1.30's own output: tap-scale stray
 * spaces, FUTO 0 of 125 right; this rule fixes 103 of 125 (test half 56 of 65), and it never
 * touches two real words typed with a space ("life style", "be held"; 0 of 219 changed). On 37,134
 * correctly typed word pairs it joins 1 ("per se"), after skipping single capitals and a capitalised
 * second piece, which are almost always initials or names.
 */
val StraySpaceMergeSetting = SettingsKey(
    booleanPreferencesKey("stray_space_merge"),
    true
)

object StraySpaceMerge {
    @JvmStatic fun isEnabled(context: android.content.Context): Boolean =
        context.getSetting(StraySpaceMergeSetting)

    private fun letters(w: String) = w.isNotEmpty() && w.all { it.isLetter() }

    /**
     * The joined word for the typed pieces [first] and [second], or null. [isWord] answers for
     * lower-case words.
     */
    @JvmStatic
    fun merged(first: String, second: String, isWord: java.util.function.Predicate<String>): String? {
        if (!letters(first) || !letters(second)) return null
        // Initials and names: "J Smith", "De Witt", "Mc Donald" are left alone
        if ((first.length == 1 && first[0].isUpperCase()) || (second.length == 1 && second[0].isUpperCase())) return null
        if (second[0].isUpperCase()) return null
        val joined = (first + second).lowercase()
        if (!isWord.test(joined)) return null
        if (isWord.test(first.lowercase()) && isWord.test(second.lowercase())) return null
        return if (first[0].isUpperCase()) joined.replaceFirstChar { it.titlecase() } else joined
    }
}
