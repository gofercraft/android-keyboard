package org.futo.inputmethod.latin

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.uix.DataStoreHelper
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.setSetting

// when the user deletes an autocorrection and types their own spelling again, keep
// that spelling and learn it at once, as if they had picked it from the strip. Without this, a word
// deleted back to nothing and retyped is a fresh word, so the next space corrects it again.
// The kept spelling is remembered, so autocorrect leaves it alone from then on.
val KeepRetypedWordSetting = SettingsKey(
    booleanPreferencesKey("keep_retyped_word"),
    true
)

// Kept spellings, newest last, one per line, lower case
val KeptRetypedWordsSetting = SettingsKey(
    stringPreferencesKey("kept_retyped_words"),
    ""
)

object KeepRetypedWord {
    private const val MAX_KEPT = 200

    // The typed word of the autocorrection the user backspaced into, until the next commit
    private var rejectedTyped: String? = null
    // A word to add to user history as valid on its next addition, whatever LearnKeptWords's setting is
    private var learnNow: String? = null
    // In-memory copy of KeptRetypedWordsSetting, so a write is seen before the datastore catches up
    private var kept: MutableList<String>? = null

    @JvmStatic
    fun isEnabled(): Boolean = DataStoreHelper.getSetting(KeepRetypedWordSetting)

    @JvmStatic
    fun onCorrectionDeleted(typed: String?, committed: String?) {
        rejectedTyped = if (!typed.isNullOrEmpty() && typed != committed && isEnabled()) typed else null
    }

    /** True if [typed] is the spelling an autocorrection replaced and the user then deleted. */
    @JvmStatic
    fun matches(typed: String?): Boolean {
        val rejected = rejectedTyped ?: return false
        return !typed.isNullOrEmpty() && rejected.equals(typed, ignoreCase = true)
    }

    private fun keptList(): MutableList<String> = kept ?: DataStoreHelper.getSetting(KeptRetypedWordsSetting)
        .split('\n').filter { it.isNotEmpty() }.toMutableList().also { kept = it }

    /** True if the user kept [typed] by retyping it before: autocorrect should leave it alone. */
    @JvmStatic
    fun isKept(typed: String?): Boolean =
        !typed.isNullOrEmpty() && isEnabled() && keptList().contains(typed.lowercase())

    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    fun remember(context: Context, word: String) {
        val list = keptList()
        val w = word.lowercase()
        list.remove(w)
        list.add(w)
        while (list.size > MAX_KEPT) list.removeAt(0)
        val joined = list.joinToString("\n")
        GlobalScope.launch { context.setSetting(KeptRetypedWordsSetting.key, joined) }
    }

    @JvmStatic
    fun clear() {
        rejectedTyped = null
    }

    /** "Delete learned words": forget every kept spelling as well. */
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    fun forgetKept(context: Context) {
        rejectedTyped = null
        learnNow = null
        kept = mutableListOf()
        GlobalScope.launch { context.setSetting(KeptRetypedWordsSetting.key, "") }
    }

    @JvmStatic
    fun setLearnNow(word: String?) {
        learnNow = word
    }

    @JvmStatic
    fun takeLearnNow(word: String): Boolean {
        val hit = learnNow == word
        learnNow = null
        return hit
    }
}
