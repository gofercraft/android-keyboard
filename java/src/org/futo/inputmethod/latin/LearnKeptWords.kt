package org.futo.inputmethod.latin

import androidx.datastore.preferences.core.booleanPreferencesKey
import org.futo.inputmethod.latin.uix.DataStoreHelper
import org.futo.inputmethod.latin.uix.SettingsKey

// learn a new word the first time the user keeps it on purpose (picks the typed
// word from the strip, or undoes an autocorrection back to it), instead of only after it has been
// typed twice. Words that were merely typed and left alone still need two commits, as before.
val LearnKeptWordsSetting = SettingsKey(
    booleanPreferencesKey("learn_kept_words"),
    false
)

object LearnKeptWords {
    @JvmStatic
    fun isEnabled(): Boolean = DataStoreHelper.getSetting(LearnKeptWordsSetting)
}
