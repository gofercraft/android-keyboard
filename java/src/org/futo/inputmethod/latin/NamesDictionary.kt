package org.futo.inputmethod.latin

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import org.futo.inputmethod.annotations.ExternallyReferenced
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.utils.ExecutorUtils
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

// about 25,000 people and place names FUTO's main wordlist lacks, from public-domain
// and CC0 lists (see the header of res/raw/names_<language>.txt). Used only as an extra swipe-decoder
// trie at a reduced weight; it is not in ALL_DICTIONARY_TYPES, so taps never see it.
val NamesDictionarySetting = SettingsKey(
    booleanPreferencesKey("names_dictionary"),
    true
)

/**
 * Read-only names list shipped as a raw resource, one `word<TAB>frequency` line per word. Unlike
 * SupplementDictionary it is not rebuilt on every load: the dictionary file is kept, and the
 * versioned name makes an app update with a changed list build a fresh file.
 */
class NamesDictionary private constructor(
    context: Context, locale: Locale, dictFile: File?, name: String
) : ExpandableBinaryDictionary(context, ExpandableBinaryDictionary.getDictName(name, locale, dictFile), locale, Dictionary.TYPE_NAMES, dictFile) {

    init {
        // Loading (about 150 ms the first time, when the file is built from the resource) runs on the
        // keyboard's single background thread and never blocks typing or swiping. Swipes made before it
        // finishes simply lack names. A task on the same thread, after the load and after the new
        // dictionary group has been installed (the delay), asks for the swipe tries to be rebuilt once,
        // on the next gesture.
        reloadDictionaryIfRequired()
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).schedule({
            DictionaryFacilitatorImpl.onBackgroundDictionaryLoaded()
        }, 500, TimeUnit.MILLISECONDS)
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
                    false /* isNotAWord */, false /* isPossiblyOffensive */,
                    BinaryDictionary.NOT_A_VALID_TIMESTAMP)
            }
        }
    }

    companion object {
        // Bump when names_<language>.txt changes, so the cached dictionary file is rebuilt
        private const val NAME = "names_v1"

        // Swipe trie weight relative to the main dictionary (fitted on swipe-2/swipe-5,
        // checked on swipe-3/swipe-4)
        const val TRIE_WEIGHT = 0.75f

        private fun resourceIdFor(context: Context, locale: Locale): Int =
            context.resources.getIdentifier("names_" + locale.language.lowercase(), "raw", context.packageName)

        @JvmStatic
        fun isEnabled(context: Context): Boolean = context.getSetting(NamesDictionarySetting)

        // Note: This method is called by {@link DictionaryFacilitator} using Java reflection.
        @JvmStatic
        @ExternallyReferenced
        fun getDictionary(
            context: Context, locale: Locale, dictFile: File?, dictNamePrefix: String, account: String?
        ): NamesDictionary = NamesDictionary(context, locale, dictFile, dictNamePrefix + NAME)
    }
}
