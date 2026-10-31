package org.futo.inputmethod.latin

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting

/**
 * a tapped word that is a real word but not the one meant ("if" for "of", "go" for
 * "to") is replaced once the next word is typed, when the language model is sure. It reuses patch
 * 13's tap decision (09's top 4 for the tapped word, the same model calls) with its own weights and
 * a high lead, and PreviousSwipeRevision's replace-and-undo path: backspace right after restores the word.
 *
 * Offline with FUTO's LM and the shipped decision: simulated taps (5,274
 * words with a next word) 29 fixed, 3 broken, 3 wrong-to-wrong; tap-scale FIT (513) 3 fixed, 0 broken.
 */
val TapRevisionSetting = SettingsKey(
    booleanPreferencesKey("previous_tap_revision"),
    false
)

object TapRevision {
    /** Weights of ln p(word | before) and ln p(next | before word), added to 09's tap score. */
    const val WORD_WEIGHT = 0.5
    const val NEXT_WEIGHT = 1.0

    /** Replace only when the other word leads the tapped one by this. */
    const val AUTO_MIN_LEAD = 6.0

    @JvmStatic fun isEnabled(context: Context): Boolean = context.getSetting(TapRevisionSetting)
}
