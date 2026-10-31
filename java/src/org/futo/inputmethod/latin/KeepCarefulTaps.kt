package org.futo.inputmethod.latin

import androidx.datastore.preferences.core.booleanPreferencesKey
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.latin.common.InputPointers
import org.futo.inputmethod.latin.uix.DataStoreHelper
import org.futo.inputmethod.latin.uix.SettingsKey

// Keep a word exactly as typed when every tap was slow and landed near the middle of its key.
// Typing a word one deliberate letter at a time is how people say "I mean this spelling", so
// autocorrect leaves it alone. Measured on the Google TSI taps (real phone taps with times): at
// 400 ms and 0.4 of a key, 2 of 1,544 real typos would have been kept.
val KeepCarefulTapsSetting = SettingsKey(
    booleanPreferencesKey("keep_careful_taps"),
    false
)

object KeepCarefulTaps {
    // Every gap between two taps of the word must be at least this long
    const val MIN_GAP_MS = 400L
    // Every tap must land within this distance of its key's centre, in key pitches
    // (sqrt((dx / pitchX)^2 + (dy / pitchY)^2))
    const val MAX_OFFSET = 0.4f
    private const val MAX_LETTERS = 48

    // Time of the tap that put each letter of the composing word in place, by position
    private val times = LongArray(MAX_LETTERS)

    @JvmStatic
    fun isEnabled(): Boolean = DataStoreHelper.getSetting(KeepCarefulTapsSetting)

    /** A letter tap just made the composing word [size] code points long. */
    @JvmStatic
    fun onLetter(size: Int, uptimeMillis: Long) {
        if (size in 1..MAX_LETTERS) times[size - 1] = uptimeMillis
    }

    /**
     * True if [typed] was tapped slowly and carefully: two or more letters, a to z only, every gap
     * at least [MIN_GAP_MS], and every tap within [MAX_OFFSET] of the centre of the key it hit.
     */
    @JvmStatic
    fun isCareful(typed: String?, pointers: InputPointers, keyboard: Keyboard?): Boolean {
        if (typed == null || typed.length < 2 || typed.length > MAX_LETTERS) return false
        if (!typed.all { it in 'a'..'z' || it in 'A'..'Z' }) return false
        if (keyboard == null || pointers.pointerSize != typed.length || !isEnabled()) return false

        for (i in 1 until typed.length) {
            if (times[i] - times[i - 1] < MIN_GAP_MS) return false
        }

        // Centre of each key as drawn, and the key pitch, as TapShapeReranker measures them
        val centres = HashMap<Char, FloatArray>()
        keyboard.sortedKeys.forEach {
            val c = Character.toLowerCase(it.code)
            if (c in 'a'.code..'z'.code) {
                centres[c.toChar()] = floatArrayOf(it.x + it.width / 2.0f, it.y + it.height / 2.0f)
            }
        }
        val q = centres['q'] ?: return false
        val p = centres['p'] ?: return false
        val z = centres['z'] ?: return false
        val pitchX = (p[0] - q[0]) / 9.0f
        val pitchY = (z[1] - q[1]) / 2.0f
        if (pitchX <= 0f || pitchY <= 0f) return false

        val xs = pointers.xCoordinates
        val ys = pointers.yCoordinates
        for (i in typed.indices) {
            if (xs[i] < 0 || ys[i] < 0) return false
            val c = centres[typed[i].lowercaseChar()] ?: return false
            val dx = (xs[i] - c[0]) / pitchX
            val dy = (ys[i] - c[1]) / pitchY
            if (dx * dx + dy * dy > MAX_OFFSET * MAX_OFFSET) return false
        }
        return true
    }
}
