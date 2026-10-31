package org.futo.inputmethod.latin

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import org.futo.inputmethod.annotations.ExternallyReferenced
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import java.io.File
import java.util.Locale

// everyday words FUTO's main wordlist lacks (idk, nah, FBI, COVID), loaded from
// res/raw/supplement_<language>.txt as a separate dictionary so the main Lexiteria-licensed
// wordlist stays untouched. Words also in the main dictionary at a very low frequency (lol, app)
// are listed too, so that they can rank; check in a build that the higher entry wins.
val SupplementDictionarySetting = SettingsKey(
    booleanPreferencesKey("supplement_dictionary"),
    true
)

/**
 * Read-only word list shipped as a raw resource, one `word<TAB>frequency[<TAB>flag]` line per word,
 * frequency on the main dictionary's 0-255 scale, flag `possibly_offensive` or empty. Lines
 * starting with # are comments.
 */
class SupplementDictionary private constructor(
    context: Context, locale: Locale, dictFile: File?, name: String
) : ExpandableBinaryDictionary(context, ExpandableBinaryDictionary.getDictName(name, locale, dictFile), locale, Dictionary.TYPE_SUPPLEMENT, dictFile) {

    init {
        // The resource ships with the app, so rebuild from it on every load; it is a few hundred words.
        setNeedsToRecreate()
        reloadDictionaryIfRequired()
    }

    override fun loadInitialContentsLocked() {
        val resId = resourceIdFor(mContext, mLocale ?: return)
        if(resId == 0) return
        mContext.resources.openRawResource(resId).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                val parts = line.split('\t')
                val word = parts[0]
                val frequency = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
                if(word.length > MAX_WORD_LENGTH) return@forEach
                runGCIfRequiredLocked(true /* mindsBlockByGC */)
                addUnigramLocked(word, frequency.coerceIn(0, 255), null /* shortcutTarget */, 0,
                    false /* isNotAWord */, parts.getOrNull(2) == "possibly_offensive",
                    BinaryDictionary.NOT_A_VALID_TIMESTAMP)
            }
        }
    }

    companion object {
        private const val NAME = "supplement"

        private fun resourceIdFor(context: Context, locale: Locale): Int =
            context.resources.getIdentifier("supplement_" + locale.language.lowercase(), "raw", context.packageName)

        @JvmStatic
        fun isEnabled(context: Context): Boolean = context.getSetting(SupplementDictionarySetting)

        // Note: This method is called by {@link DictionaryFacilitator} using Java reflection.
        @JvmStatic
        @ExternallyReferenced
        fun getDictionary(
            context: Context, locale: Locale, dictFile: File?, dictNamePrefix: String, account: String?
        ): SupplementDictionary = SupplementDictionary(context, locale, dictFile, dictNamePrefix + NAME)
    }
}
