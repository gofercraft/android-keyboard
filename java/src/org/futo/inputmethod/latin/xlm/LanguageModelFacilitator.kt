package org.futo.inputmethod.latin.xlm;

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.futo.inputmethod.engine.general.OnGetSuggestedWordsCallbackWithInputStyle
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.keyboard.KeyboardSwitcher
import org.futo.inputmethod.latin.BinaryDictionary
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.Dictionary
import org.futo.inputmethod.latin.DictionaryFacilitator
import org.futo.inputmethod.latin.NgramContext
import org.futo.inputmethod.latin.Suggest
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.SuggestionBlacklist
import org.futo.inputmethod.latin.TapShapeReranker
import org.futo.inputmethod.latin.common.ComposedData
import org.futo.inputmethod.latin.inputlogic.InputLogic
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.USE_TRANSFORMER_FINETUNING
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.utils.SuggestionResults
import kotlin.math.ceil
import kotlin.math.ln


val AutocorrectThresholdSetting = SettingsKey(
    floatPreferencesKey("lm_autocorrect_threshold"),
    4.0f
)

// when nothing else autocorrects an unknown typed word, try swapping each pair
// of adjacent letters (mnoday -> monday, Jmaes -> James). Offline with FUTO's dictionary: fixes
// 191 of 196 transposition typos (the other 5 are valid words) and 18 of 19 misspelled names;
// changes 5 of 800 other typos to a wrong word at frequency >= 100.
val SwappedLetterCorrectionSetting = SettingsKey(
    booleanPreferencesKey("swapped_letter_correction"),
    true
)

/** Minimum dictionary frequency (0-255 scale) for a swapped-letter candidate. */
const val SWAPPED_LETTER_MIN_FREQUENCY = 100

val BinaryDictTransformerWeightSetting = SettingsKey(
    floatPreferencesKey("binary_dict_result_weight"),
    3.4f
)

val AllowTransformerOnNonQWERTYLayouts = SettingsKey(
    booleanPreferencesKey("allow_transformer_lm_on_non_qwerty"),
    false
)

// after the swipe shape re-rank (SwipeShapeReranker), add 0.2 x ln p(first token | previous
// words) from the transformer LM and re-pick the swipe word. Offline with FUTO's own
// rescoreSuggestionsNative: swipe-5 92.67% -> 92.95%, swipe-2 93.07% -> 93.44%. The model runs only when
// 08's top two are within 2.0 (about 12% of swipes): mean 3.6 ms, p95 16 ms per swipe on a desktop core. Off by default until it is timed on a phone.
val TransformerSwipeRescoreSetting = SettingsKey(
    booleanPreferencesKey("transformer_swipe_rescore"),
    false
)

/** Weight of ln p(first token) against the SwipeShapeReranker score, fitted on FIT. */
const val TRANSFORMER_SWIPE_WEIGHT = 0.2

const val TRANSFORMER_SWIPE_TIMEOUT_MS = 150L

/**
 * Skip the re-score when 08's best word leads its runner-up by this much (fitted on FIT). The model
 * then runs on about 12% of swipes and keeps the whole gain. A lead above 4.1 can never flip, because
 * 0.2 x ln(1e-9) = -4.1.
 */
const val TRANSFORMER_SWIPE_SKIP_MARGIN = 2.0

internal fun SuggestedWordInfo.add(other: SuggestedWordInfo): SuggestedWordInfo {
    assert(mWord == other.mWord)

    val result = SuggestedWordInfo(
        mWord,
        mPrevWordsContext,
        (mScore.coerceAtLeast(0).toLong() + other.mScore.coerceAtLeast(0).toLong())
            .coerceAtMost(
                Int.MAX_VALUE.toLong()
            ).toInt(),
        SuggestedWordInfo.KIND_WHITELIST or SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION,
        mSourceDict ?: other.mSourceDict,
        0,
        0
    )

    result.mOriginatesFromTransformerLM = mOriginatesFromTransformerLM || other.mOriginatesFromTransformerLM

    return result
}


internal fun SuggestedWordInfo.scoreAtLeast(other: SuggestedWordInfo): SuggestedWordInfo {
    val result = SuggestedWordInfo(
        mWord,
        mPrevWordsContext,
        mScore.coerceAtLeast(other.mScore + 1),
        SuggestedWordInfo.KIND_WHITELIST or SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION,
        mSourceDict,
        0,
        0
    )

    return result
}

internal fun SuggestedWordInfo.scoreAtMost(other: SuggestedWordInfo): SuggestedWordInfo {
    val result = SuggestedWordInfo(
        mWord,
        mPrevWordsContext,
        mScore.coerceAtMost(other.mScore - 1),
        mKindAndFlags,
        mSourceDict,
        0,
        0
    )

    return result
}

private fun levenshteinDistance(s1: String, s2: String): Int {
    val len1 = s1.length
    val len2 = s2.length

    val dist = Array(len1 + 1) { IntArray(len2 + 1) }

    for (i in 0..len1) {
        dist[i][0] = i
    }
    for (j in 0..len2) {
        dist[0][j] = j
    }

    for (j in 1..len2) {
        for (i in 1..len1) {
            val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
            dist[i][j] = minOf(
                dist[i - 1][j] + 1,
                dist[i][j - 1] + 1,
                dist[i - 1][j - 1] + cost
            )
        }
    }

    return dist[len1][len2]
}

public class LanguageModelFacilitator(
    val context: Context,
    val inputLogic: InputLogic,
    val dictionaryFacilitator: DictionaryFacilitator,
    val settings: Settings,
    val keyboardSwitcher: KeyboardSwitcher,
    val lifecycleScope: LifecycleCoroutineScope,
    val suggestionBlacklist: SuggestionBlacklist,
    val suggestedWordsCallback: OnGetSuggestedWordsCallbackWithInputStyle
) {
    private val TAG = "LanguageModelFacilitator"
    private val userDictionary = UserDictionaryObserver(context)

    private var languageModel: LanguageModel? = null
    data class PredictionInputValues(
        val composedData: ComposedData,
        val ngramContext: NgramContext,
        val inputStyle: Int,
        val sequenceId: Int
    )
    private val sharedFlow = MutableSharedFlow<PredictionInputValues>(replay = 0, extraBufferCapacity = 1)

    private var currentSequenceId = 0
    private val sequenceIdFinishedFlow = MutableSharedFlow<Pair<PredictionInputValues, SuggestedWords?>>(replay = 1, extraBufferCapacity = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val languageModelScope = LanguageModelScope

    private var numConsecutiveTimeouts = 0
    private var transformerDisabled = false

    fun reportTimeout() {
        if(shouldPassThroughToLegacy()) return

        if(BuildConfig.DEBUG) Log.d(TAG, "Failed to complete prediction within the time!")
        numConsecutiveTimeouts += 1
        if(numConsecutiveTimeouts > 5) {
            transformerDisabled = true
            if(BuildConfig.DEBUG) Log.w(TAG, "Temporarily disabling transformer due to continuous timeouts")
        }
    }

    private var skipLanguage: String? = null
    private suspend fun runLanguageModel(values: PredictionInputValues): ArrayList<SuggestedWordInfo>? {
        if(transformerDisabled) return null

        val locale = dictionaryFacilitator.primaryLocale ?: return null
        if ((languageModel == null && locale.language != skipLanguage) || (languageModel != null && languageModel?.locale?.language != locale.language)) {
            skipLanguage = null
            if(BuildConfig.DEBUG) Log.d(TAG, "Calling closeInternalLocked on model due to seeming locale change")
            languageModel?.closeInternalLocked()
            languageModel = null

            // TODO: Cache value so we're not hitting this repeatedly
            val options = ModelPaths.getModelOptions(context)
            val model = options[locale.language]
            if (model != null) {
                languageModel = LanguageModel(context, lifecycleScope, model, locale)
            } else {
                if(BuildConfig.DEBUG) Log.d(TAG, "no model for ${locale.language}")
                skipLanguage = locale.language
                return null
            }
        }

        if(dictionaryFacilitator.mostConfidentLocale != languageModel?.locale) return null

        val keyboard = keyboardSwitcher.keyboard ?: return null

        val proximityInfoHandle = keyboard.proximityInfo.nativeProximityInfo

        val autocorrectThreshold = context.getSetting(AutocorrectThresholdSetting)

        try {
            return languageModel?.getSuggestions(
                values.composedData,
                values.ngramContext,
                proximityInfoHandle,
                autocorrectThreshold,
                userDictionary.getWords(listOf(locale)).map { it.word },
                arrayOf()
            )
        }catch (e: ModelLoadingException) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Unable to load Transformer model for ${locale.getDisplayLanguage(locale)}, it may be corrupted or unsupported.",
                    Toast.LENGTH_LONG
                ).show()
                transformerDisabled = true
                e.printStackTrace()
            }
            return null
        }
    }

    suspend fun getLanguageModelSuggestions(values: PredictionInputValues): ArrayList<SuggestedWordInfo>? {
        if(values.composedData.mTypedWord.length > BinaryDictionary.DICTIONARY_MAX_WORD_LENGTH-1)
            return null

        val lmSuggestions = runLanguageModel(values)
        return lmSuggestions
    }

    fun processAndMergeSuggestions(
        values: PredictionInputValues,
        suggestedWordsDict: SuggestedWords,
        lmSuggestions: ArrayList<SuggestedWordInfo>
    ): SuggestedWords? {
        var transformerWeight = context.getSetting(BinaryDictTransformerWeightSetting)
        if(dictionaryFacilitator.locales.size > 1) transformerWeight = 1.0f

        val suggestionResults = SuggestionResults(
            14, values.ngramContext.isBeginningOfSentenceContext, false)


        val reweightedSuggestions = lmSuggestions.mapIndexedNotNull { i, it ->
            if(transformerWeight == Float.NEGATIVE_INFINITY) { null } else {
                SuggestedWordInfo(
                    it.mWord,
                    it.mPrevWordsContext,
                    (it.mScore.toFloat() * transformerWeight).toLong().coerceAtMost(Int.MAX_VALUE.toLong() - lmSuggestions.size)
                        .toInt() - i + (lmSuggestions.size - 1),
                    it.mKindAndFlags,
                    it.mSourceDict,
                    it.mIndexOfTouchPointOfSecondWord,
                    it.mAutoCommitFirstWordConfidence
                ).apply {
                    this.mOriginatesFromTransformerLM = true
                }
            }
        }

        val maxWord = reweightedSuggestions.maxByOrNull { it.mScore }

        val suggestedWordsDictList = suggestedWordsDict.mSuggestedWordInfoList.filter {
            suggestionBlacklist.isSuggestedWordOk(it)
        }.toMutableList()

        var maxWordDict = suggestedWordsDictList.maxByOrNull {
            if(it == suggestedWordsDict.typedWordInfo
                || it.isKindOf(SuggestedWordInfo.KIND_EMOJI_SUGGESTION)) Int.MIN_VALUE else it.mScore
        }

        val maxNonWhitelistWordDict = suggestedWordsDictList.maxByOrNull {
            if(it == suggestedWordsDict.typedWordInfo
                || it.isKindOf(SuggestedWordInfo.KIND_EMOJI_SUGGESTION)
                || (it.isKindOf(SuggestedWordInfo.KIND_WHITELIST) && it.mSourceDict?.mDictType == "main")) Int.MIN_VALUE else it.mScore
        }

        // English language has some shortcuts, e.g. "bot" -> "not",    "hid" -> "his"
        // These are common misspellings and usually useful corrections, but if the language model
        // believes the original word fits better in the context we should skip the shortcut,
        // else it's impossible to type these words without manually skipping correction in
        // suggestion bar
        if(maxWordDict != null && maxNonWhitelistWordDict != null && maxNonWhitelistWordDict != maxWordDict && maxNonWhitelistWordDict.mWord == maxWord?.mWord) {
            val idx = suggestedWordsDictList.indexOf(maxWordDict)
            suggestedWordsDictList.remove(maxWordDict)
            suggestedWordsDictList.add(idx, maxWordDict.scoreAtMost(maxNonWhitelistWordDict))
            maxWordDict = maxNonWhitelistWordDict
        }

        val bothAlgorithmsCameToSameConclusion = maxWordDict?.mWord == maxWord?.mWord

        var autocorrectWord: SuggestedWordInfo? = null
        val filtered = mutableListOf<SuggestedWordInfo>()
        if(bothAlgorithmsCameToSameConclusion && maxWord != null && maxWordDict != null){
            if(BuildConfig.DEBUG) Log.d(TAG, "both algorithms came to same conclusion, autocorrect to ${maxWord.mWord}")
            // We can be pretty confident about autocorrecting this
            val clone = maxWord.add(maxWordDict)
            autocorrectWord = clone
            suggestionResults.add(clone)
            filtered.add(maxWordDict)
            filtered.add(maxWord)
        }
        // In some cases the LM will predict an uppercased word but dictionary predicts lowercased,
        // we should prefer the lowercase version to reduce automatically capitalizing which can be
        // annoying
        val bothAlgorithmsCameToSameConclusionButLowerCased = maxWordDict?.mWord == maxWord?.mWord?.lowercase()
        if(bothAlgorithmsCameToSameConclusionButLowerCased && maxWord != null && maxWordDict != null) {
            if(BuildConfig.DEBUG) Log.d(TAG, "both algorithms came to same conclusion but lowercased, autocorrect to ${maxWord.mWord}")
            val clone = maxWordDict.scoreAtLeast(maxWord)
            autocorrectWord = clone
            suggestionResults.add(clone)
            filtered.add(maxWordDict)
        }

        if(transformerWeight <= 0.0f) {
            if(suggestedWordsDictList.isEmpty()) {
                transformerWeight = 1.0f
            }
        }

        // Add reweightedSuggestions, with space replacement logic. It can replace one of the LM
        // suggestions if the top dictionary result has a space, based on heuristics about the
        // relative quality of the LM suggestion
        val spaceReplacementPossible = maxWordDict != null && maxWordDict.word.count { it == ' ' } == 1
        var spaceReplacementPerformed = false
        for(i in 0 until reweightedSuggestions.size) {
            val word = reweightedSuggestions[i]
            if(filtered.contains(word)) continue

            if(!spaceReplacementPerformed && spaceReplacementPossible && (
                        // If the dict score is high enough, allow the space suggestion
                        ((maxWordDict.mScore) > (word.mScore / 3))
                                // Most LM-generated dashed suggestions are distractions, so accept the space suggestion
                                || (word.word.contains('-'))
                                // If the typed word is much longer than the transformer word, just accept the space suggestion
                                || (values.composedData.mTypedWord.length > ceil(word.word.length * 3.0 / 2.0))
                        )
            ) {
                val clone = maxWordDict.scoreAtLeast(word)
                suggestionResults.add(clone)
                spaceReplacementPerformed = true
                continue
            }

            suggestionResults.add(word)
        }

        if(maxWordDict?.mSourceDict?.mDictType == Dictionary.TYPE_USER_HISTORY
            && maxWordDict.mScore > 100
            && maxWord != null
            && (maxWordDict.mWord == values.composedData.mTypedWord || maxWordDict.mWord.length > 1)
        ) {
            if(BuildConfig.DEBUG) Log.d(TAG, "type user history found")
            val clone = maxWordDict.scoreAtLeast(maxWord)
            suggestionResults.add(clone)
        }

        suggestionResults.mRawSuggestions?.addAll(reweightedSuggestions.filter { !filtered.contains(it) })
        if(transformerWeight != Float.POSITIVE_INFINITY) {
            suggestedWordsDictList?.let { words ->
                suggestionResults.addAll(words.filter {
                    it != suggestedWordsDict.typedWordInfo && !filtered.contains(
                        it
                    )
                })
            }
        }

        if(autocorrectWord == null && context.getSetting(SwappedLetterCorrectionSetting)) {
            swappedLetterCorrection(values)?.let { word ->
                val topScore = suggestionResults.firstOrNull()?.mScore ?: 0
                suggestionResults.add(SuggestedWordInfo(
                    word, "", topScore + 1, SuggestedWordInfo.KIND_CORRECTION,
                    maxWordDict?.mSourceDict ?: Dictionary.DICTIONARY_USER_TYPED,
                    SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE
                ))
                suggestionResults.mForceAutoCorrection = true
            }
        }

        val settingsValues = settings.current ?: return null
        val locale = dictionaryFacilitator.primaryLocale ?: return null
        val wordComposer = inputLogic.mWordComposer ?: return null

        val suggestedWords = Suggest.obtainNonBatchedInputSuggestedWords(
            wordComposer,
            values.inputStyle,
            settingsValues.mAutoCorrectionEnabledPerUserSettings,
            -1,
            locale,
            suggestionResults,
            settingsValues.mAutoCorrectionThreshold,
            settingsValues.mIsNumberRowEnabled
        )

        if(context.getSetting(TapShapeRerankSetting)) {
            tapShapeReranked(values, suggestedWords, suggestionResults.mForceAutoCorrection)?.let { return it }
        }


        if(BuildConfig.DEBUG) {
            val dmpw: (SuggestedWordInfo?) -> String = { v -> v?.let { "${it.mWord}:${it.mScore}:${it.mKindAndFlags}:${it.mSourceDict?.mDictType}" } ?: "[null]" }
            val dmp: (List<SuggestedWordInfo>?) -> String = { v -> v?.joinToString { dmpw(it) } ?: "[null]" }
            Log.d(TAG, "process update suggestion strip:\n" +
                    "raw lm results: ${dmp(lmSuggestions)}\n" +
                    "reweighted lm results: ${dmp(reweightedSuggestions)}\n" +
                    "raw dict results: ${dmp(suggestedWordsDict?.mSuggestedWordInfoList)}}\n" +
                    "filtered dict results: ${dmp(suggestedWordsDictList)}}\n" +
                    "------------\n" +
                    "max word lm: ${dmpw(maxWord)}}\n" +
                    "max word dict: ${dmpw(maxWordDict)}}\n"
            )
        }


        return suggestedWords
    }

    /**
     * Returns the most frequent dictionary word made by swapping two adjacent letters of an
     * unknown typed word, keeping a capital first letter if the user typed one. Tries the
     * lowercase and capitalised spellings so names (james -> James) are found.
     */
    private fun swappedLetterCorrection(values: PredictionInputValues): String? {
        val typed = values.composedData.mTypedWord
        if(typed.length < 3 || !typed.all { it.isLetter() }) return null
        if(typed.drop(1).any { it.isUpperCase() }) return null
        if(dictionaryFacilitator.isValidSuggestionWord(typed)
            || dictionaryFacilitator.isValidSuggestionWord(typed.lowercase())) return null

        val lower = typed.lowercase()
        var best: String? = null
        var bestFreq = SWAPPED_LETTER_MIN_FREQUENCY - 1
        for(i in 0 until lower.length - 1) {
            if(lower[i] == lower[i + 1]) continue
            val swapped = lower.substring(0, i) + lower[i + 1] + lower[i] + lower.substring(i + 2)
            for(form in listOf(swapped, swapped.replaceFirstChar { it.titlecase() })) {
                val freq = dictionaryFacilitator.getWordFrequency(form)
                if(freq > bestFreq) { best = form; bestFreq = freq }
            }
        }
        if(best != null && typed[0].isUpperCase()) best = best.replaceFirstChar { it.titlecase() }
        return best
    }

    public suspend fun destroyModel() {
        if(BuildConfig.DEBUG) Log.d(TAG, "destroyModel called")
        languageModel?.closeInternalLocked()
        languageModel = null
    }

    public fun close() {
        userDictionary.unregister()
    }

    private var trainingEnabled = false

    public fun launchProcessor() = lifecycleScope.launch {
        if(BuildConfig.DEBUG) Log.d(TAG, "Starting processor")
        launch {
            withContext(Dispatchers.Default) {
                TrainingWorkerStatus.lmRequest.collect {
                    if (it == LanguageModelFacilitatorRequest.ResetModel) {
                        if(BuildConfig.DEBUG) Log.d(TAG, "ResetModel event received, destroying model")
                        destroyModel()
                    }else if(it == LanguageModelFacilitatorRequest.ClearTrainingLog) {
                        historyLog.clear()
                        saveHistoryLog()
                    }
                }
            }
        }

        launch {
            withContext(Dispatchers.Default) {
                ModelPaths.modelOptionsUpdated.collect {
                    if(BuildConfig.DEBUG) Log.d(TAG, "ModelPaths options updated, destroying model")
                    skipLanguage = null
                    destroyModel()
                }
            }
        }
    }

    public fun shouldPassThroughToLegacy(): Boolean = when {
        (!settings.current.mTransformerPredictionEnabled) -> true
        (dictionaryFacilitator.primaryLocale.language == skipLanguage) -> true
        (transformerDisabled) -> true
        else -> false
    }

    /**
     * re-ranks the tap strip with where the taps landed (TapShapeReranker).
     * Returns null to keep FUTO's strip and choice. [forced]: another correction (swapped letters,
     * copula -ing) already forced its word; the taps are still scored but that word is kept.
     */
    private fun tapShapeReranked(values: PredictionInputValues, words: SuggestedWords, forced: Boolean): SuggestedWords? {
        val composed = values.composedData
        if(composed.mIsBatchMode || words.isPrediction || words.size() < 2) return null
        val keyboard = keyboardSwitcher.keyboard ?: return null

        val centres = HashMap<Char, FloatArray>()
        keyboard.sortedKeys.forEach {
            val c = Character.toLowerCase(it.code)
            if(c in 'a'.code..'z'.code) {
                // Centre of the key as drawn, where people aim. (The host's simulated taps aim at the
                // hit-box centre instead, about 0.04 key widths right and 0.07 key heights lower.)
                centres[c.toChar()] = floatArrayOf(it.x + it.width / 2.0f, it.y + it.height / 2.0f)
            }
        }
        val q = centres['q'] ?: return null
        val p = centres['p'] ?: return null
        val z = centres['z'] ?: return null
        // Key pitch (centre to centre), the unit the re-ranker was fitted in
        val keyWidth = (p[0] - q[0]) / 9.0f
        val keyHeight = (z[1] - q[1]) / 2.0f
        if(keyWidth <= 0f || keyHeight <= 0f) return null
        val keys: Map<Char, FloatArray> = centres.mapValues { floatArrayOf(it.value[0] / keyWidth, it.value[1] / keyHeight) }

        val pointers = composed.mInputPointers
        val xs = pointers.xCoordinates
        val ys = pointers.yCoordinates
        val tx = ArrayList<Float>()
        val ty = ArrayList<Float>()
        for(i in 0 until pointers.pointerSize) {
            if(xs[i] < 0 || ys[i] < 0) continue
            tx.add(xs[i] / keyWidth)
            ty.add(ys[i] / keyHeight)
        }
        if(tx.isEmpty()) return null

        val list = words.mSuggestedWordInfoList
        val tapScores = DoubleArray(list.size)
        val pick = TapShapeReranker.choose(
            tx.toFloatArray(), ty.toFloatArray(), keys,
            Array(list.size) { list[it].mWord }, IntArray(list.size) { list[it].mScore },
            if(words.mWillAutoCorrect) 1 else 0, words.mTypedWordValid, tapScores
        )
        val top4 = list.indices.filter { !tapScores[it].isNaN() }.sortedByDescending { tapScores[it] }.take(4)
        org.futo.inputmethod.latin.LastTapShapeScores.set(top4.map { list[it].mWord }, top4.map { tapScores[it] })
        if(pick < 0) return null
        if(forced) return null

        // Index 0 is the typed word; the word at index 1 is committed when willAutoCorrect is set
        val reordered = ArrayList(list)
        if(pick > 1) reordered.add(1, reordered.removeAt(pick))
        return SuggestedWords(
            reordered, words.mRawSuggestions, words.mTypedWordInfo, words.mTypedWordValid,
            pick != 0, words.mIsObsoleteSuggestions, words.mInputStyle, words.mSequenceNumber
        )
    }

    // This method should return null if transformer is disabled by settings or locale
    var prevAcceptedLayout: Keyboard? = null
    var prevAcceptedLayoutResult = false
    fun makePredictionInputValues(inputStyle: Int): PredictionInputValues? {
        if(shouldPassThroughToLegacy()) return null

        if(inputStyle == SuggestedWords.INPUT_STYLE_TAIL_BATCH
            || inputStyle == SuggestedWords.INPUT_STYLE_UPDATE_BATCH) return null

        val settingsValues = settings.current
        if (!settingsValues.needsToLookupSuggestions()) {
            return null
        }

        if(settingsValues.mInputAttributes.mIsEmailField) return null

        if(!inputLogic.mConnection.isConnected) return null

        try {
            val wordComposer = inputLogic.mWordComposer
            val ngramContext = inputLogic.getNgramContextFromNthPreviousWordForSuggestion(
                settingsValues.mSpacingAndPunctuations,
                2
            )

            if(wordComposer.isComposingWord
                && !context.getSetting(AllowTransformerOnNonQWERTYLayouts)
            ) {
                // Check we are on a supported layout
                if(prevAcceptedLayout != keyboardSwitcher.keyboard) {
                    if(keyboardSwitcher.keyboard?.mId?.mKeyboardLayoutSetName == "qwerty") {
                        prevAcceptedLayoutResult = true
                    } else {
                        val letters = keyboardSwitcher.keyboard?.sortedKeys?.filter {
                            settingsValues.isWordCodePoint(it.code) && it.code != '\''.code
                        }?.sortedBy {
                            it.x + 10000 * it.y
                        }?.joinToString(separator = "") { it.label.lowercase() } ?: ""

                        prevAcceptedLayoutResult = letters == "qwertyuiopasdfghjklzxcvbnm"
                    }
                }

                if(prevAcceptedLayoutResult == false)
                    return null
            }

            val values = PredictionInputValues(
                wordComposer.composedDataSnapshot,
                ngramContext,
                inputStyle,
                ++currentSequenceId
            )

            return values
        } catch(e: Exception) {
            if(e is CancellationException) throw e

            if(BuildConfig.DEBUG) Log.d(TAG, "Failed to get context, composed data snapshot, etc: $e")
            e.printStackTrace()
        }

        return null
    }

    private val historyLog: MutableList<HistoryLogForTraining> = mutableListOf()

    public fun addToHistory(
        word: String,
        wasAutoCapitalized: Boolean,
        ngramContext: NgramContext,
        timeStampInSeconds: Long,
        blockPotentiallyOffensive: Boolean,
        importance: Int
    ) {
        if(shouldPassThroughToLegacy()) return
        if(!trainingEnabled) return
        if(settings.current?.mInputAttributes?.mNoLearning != false) return

        if(dictionaryFacilitator.mostConfidentLocale != languageModel?.locale) return

        val wordCtx = ngramContext.fullContext.trim().lines().last()
        var committedNgramCtx = ngramContext.extractPrevWordsContext().replace(NgramContext.BEGINNING_OF_SENTENCE_TAG, " ").trim();
        if(committedNgramCtx.isEmpty()) {
            committedNgramCtx = " "
        }
        
        val lastIdx = wordCtx.lastIndexOf(committedNgramCtx)
        if(lastIdx == -1) {
            //println("addToHistory: extraction failed, couldn't find ngram ctx in full ctx")
            return
        }

        val misspelledWord = wordCtx.substring(
            lastIdx + committedNgramCtx.length
        )
        if(misspelledWord.isNotBlank() && (!(misspelledWord.startsWith(" ") || committedNgramCtx == " ") || misspelledWord.endsWith(" ") || misspelledWord.trim().contains(" "))) {
            //println("addToHistory: extraction failed bad context. wordCtx=[$wordCtx]  --   committedNgramCtx=[$committedNgramCtx]  --  word=[$word]  --  fullNgram=[$ngramContext]")
            return
        }

        val ctxBeforeMisspelledWord = wordCtx.dropLast(misspelledWord.length)

        val key = committedNgramCtx.trim() + " " + word.trim()
        val logToAdd = if(misspelledWord.isNotBlank()) {
            // Correcting (ctx) misspelled -> word
            HistoryLogForTraining(
                key,
                ctxBeforeMisspelledWord,
                committedNgramCtx,
                misspelledWord.trim(),
                word,
                importance,
                dictionaryFacilitator.primaryLocale.language,
                timeStampInSeconds
            )
        } else {
            // Predicted (ctx) -> word
            HistoryLogForTraining(
                key,
                ctxBeforeMisspelledWord,
                committedNgramCtx,
                null,
                word,
                importance,
                dictionaryFacilitator.primaryLocale.language,
                timeStampInSeconds
            )
        }

        historyLog.add(logToAdd)
        //println("addToHistory: Adding $logToAdd")
    }

    public fun unlearnFromHistory(
        word: String,
        ngramContext: NgramContext,
        timeStampInSeconds: Long,
        eventType: Int
    ) {
        if(shouldPassThroughToLegacy()) return
        if(!trainingEnabled) return

        val wordCtx = ngramContext.fullContext.trim().lines().last()
        var committedNgramCtx = ngramContext.extractPrevWordsContext().replace(NgramContext.BEGINNING_OF_SENTENCE_TAG, " ").trim();
        if(committedNgramCtx.isEmpty()) {
            committedNgramCtx = " "
        }
        
        val keyToSearch = committedNgramCtx.trim() + " " + word.trim()

        val logToRemove = historyLog.indexOfLast {
            it.key.startsWith(keyToSearch) || it.key == keyToSearch
        }

        if(logToRemove == -1) {
            //println("addToHistory: UNLEARN Couldn't find key $keyToSearch")
        } else {
            //println("addToHistory: Unlearning ${historyLog[logToRemove]}")
            historyLog.removeAt(logToRemove)
        }
    }

    public fun saveHistoryLog() {
        if(!context.getSetting(USE_TRANSFORMER_FINETUNING)) historyLog.clear()
        saveHistoryLogBackup(context, historyLog)
    }

    public fun loadHistoryLog() {
        assert(historyLog.isEmpty())
        loadHistoryLogBackup(context, historyLog)
    }

    /**
     * re-picks the swipe word as SwipeShapeReranker score + TRANSFORMER_SWIPE_WEIGHT *
     * ln p(first token | previous words). Returns null to keep the decoder's list.
     */
    suspend fun swipeTransformerRescored(words: SuggestedWords): SuggestedWords? {
        if(!context.getSetting(TransformerSwipeRescoreSetting)) return null
        val (stashWords, stashScores) = org.futo.inputmethod.latin.LastSwipeShapeScores.take() ?: return null
        val list = words.mSuggestedWordInfoList
        if(list.size < 2 || stashScores.size < 2) return null
        // A clear SwipeShapeReranker winner is never overturned in practice: skip the model call
        if(stashScores[0] - stashScores[1] >= TRANSFORMER_SWIPE_SKIP_MARGIN) return null
        // Only when the strip is exactly what SwipeShapeReranker handed out (not 02's one-letter word, etc.)
        val shape = HashMap<String, Double>()
        stashWords.forEachIndexed { i, w -> shape.putIfAbsent(w.lowercase(), stashScores[i]) }
        if(list[0].mWord.lowercase() != stashWords[0].lowercase()) return null
        if(list.any { !shape.containsKey(it.mWord.lowercase()) }) return null

        val model = languageModel ?: return null
        if(dictionaryFacilitator.mostConfidentLocale != model.locale) return null
        val ngramContext = inputLogic.getNgramContextFromNthPreviousWordForSuggestion(
            settings.current.mSpacingAndPunctuations, 2
        )
        val probs = withTimeoutOrNull(TRANSFORMER_SWIPE_TIMEOUT_MS) {
            model.firstTokenProbabilities(list.map { it.mWord }, ngramContext)
        } ?: return null

        val scored = list.indices.map { i ->
            shape[list[i].mWord.lowercase()]!! + TRANSFORMER_SWIPE_WEIGHT * ln(maxOf(probs[i], 1e-9f).toDouble())
        }
        val best = scored.indices.maxByOrNull { scored[it] } ?: return null
        if(best == 0) return null
        // Same word with another case, apostrophe or hyphen: keep the decoder's spelling
        val bare = { w: String -> w.lowercase().replace("'", "").replace("-", "") }
        if(bare(list[best].mWord) == bare(list[0].mWord)) return null

        val reordered = ArrayList(list)
        reordered.add(0, reordered.removeAt(best))
        return SuggestedWords(
            reordered, words.mRawSuggestions, reordered[0], words.mTypedWordValid,
            words.mWillAutoCorrect, words.mIsObsoleteSuggestions, words.mInputStyle, words.mSequenceNumber
        )
    }

    /**
     * the revised spelling of the previous swiped word, or null to keep it. Scores
     * SwipeShapeReranker's top 4 by shape score + WORD_WEIGHT ln p(word | before) + NEXT_WEIGHT ln p(next | before + word).
     */
    /**
     * what to do with the previous word once the next word is known. Scores the
     * candidates (SwipeShapeReranker's top 4 for a swipe, TapShapeReranker's top 4 for a tapped word) by shape/tap
     * score + w1 ln p(word | before) + w2 ln p(next | before + word). Returns the best other
     * word, and whether it clears PreviousSwipeRevision's auto-revise guards (swipes only) or only the
     * PreviousWordOffer offer threshold. Null to leave the word alone.
     */
    suspend fun previousWordDecision(
        request: org.futo.inputmethod.latin.PreviousSwipeRevision.Request
    ): org.futo.inputmethod.latin.PreviousWordDecision? {
        val r = org.futo.inputmethod.latin.PreviousSwipeRevision
        val o = org.futo.inputmethod.latin.PreviousWordOffer
        val model = languageModel ?: return null
        if(dictionaryFacilitator.mostConfidentLocale != model.locale) return null
        val words = request.candidates
        val shape = request.shapeScores
        if(words.size < 2) return null
        // A clear shape/tap winner is left alone, before any model call
        if(shape[0] - shape[1] >= (if(request.swiped) r.MAX_SHAPE_MARGIN else o.TAP_MAX_SHAPE_MARGIN)) return null
        val current = words.indexOfFirst { it.equals(request.original, ignoreCase = true) }
        if(current < 0) return null
        val w1 = if(request.swiped) r.WORD_WEIGHT else o.TAP_WORD_WEIGHT
        val w2 = if(request.swiped) r.NEXT_WEIGHT else o.TAP_NEXT_WEIGHT
        val pWord = model.firstTokenProbabilitiesForContext(words, request.context) ?: return null
        val partial = words.indices.map { shape[it] + w1 * ln(maxOf(pWord[it], 1e-9f).toDouble()) }
        // p(next | ...) only for the word on screen and the best 2 others: 4 model calls, not 5
        val others = words.indices
            .filter { !r.sameBareWord(words[it], request.original) }
            .sortedByDescending { partial[it] }.take(o.NEXT_SCORED_OTHERS)
        if(others.isEmpty()) return null
        val scores = DoubleArray(words.size) { Double.NEGATIVE_INFINITY }
        for(i in others + current) {
            val pNext = model.firstTokenProbabilitiesForContext(
                listOf(request.next), request.context + " " + words[i]
            )?.get(0) ?: return null
            scores[i] = partial[i] + w2 * ln(maxOf(pNext, 1e-9f).toDouble())
        }
        val bestIndex = others.maxByOrNull { scores[it] } ?: return null
        val lead = scores[bestIndex] - scores[current]
        // Keep a capital the keyboard gave the original (sentence start)
        val best = words[bestIndex].let {
            if(request.original.first().isUpperCase() && it.first().isLowerCase()) it.replaceFirstChar { c -> c.titlecase() } else it
        }
        val auto = request.swiped && lead >= r.MIN_SCORE_LEAD &&
                maxOf(best.length, request.original.length) <= r.MAX_WORD_LENGTH
        val offerGap = if(request.swiped) o.SWIPE_OFFER_MIN_LEAD else o.TAP_OFFER_MIN_LEAD
        if(!auto && lead < offerGap) return null
        return org.futo.inputmethod.latin.PreviousWordDecision(best, auto)
    }

    public fun onStartInput() {
        transformerDisabled = false
        numConsecutiveTimeouts = 0
    }

    public fun isTransformerDisabled(): Boolean = transformerDisabled
}

// re-rank the tap strip with where the taps landed. On the held-out half of the
// host tap cases (4,588, never trained on) FUTO goes 79.95% -> 82.13% (110 fixed, 10 broken);
// on simulated natural taps 92.96% -> 94.60% with nothing broken.
val TapShapeRerankSetting = SettingsKey(
    booleanPreferencesKey("tap_shape_rerank"),
    true
)
