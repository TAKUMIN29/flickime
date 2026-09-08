package com.example.flickime

import android.content.Context

/** SharedPreferences の薄いラッパー。 */
class Prefs(context: Context) {

    companion object {
        private const val NAME = "flick_ime_prefs"
        private const val KEY_CLIPBOARD = "clipboard_enabled"
        private const val KEY_HAPTIC = "haptic_enabled"
        private const val KEY_KEY_HEIGHT = "key_height_dp"
        private const val KEY_FLICK_THRESHOLD = "flick_threshold_dp"
        private const val KEY_ONE_HANDED = "one_handed_mode"
        private const val KEY_KANJI_CONVERSION = "kanji_conversion_enabled"

        const val DEFAULT_KEY_HEIGHT_DP = 52
        const val DEFAULT_FLICK_THRESHOLD_DP = 22
    }

    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var clipboardEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLIPBOARD, true)
        set(value) = prefs.edit().putBoolean(KEY_CLIPBOARD, value).apply()

    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC, value).apply()

    /** キー1行の高さ(dp)。40〜76 の範囲。 */
    var keyHeightDp: Int
        get() = prefs.getInt(KEY_KEY_HEIGHT, DEFAULT_KEY_HEIGHT_DP)
        set(value) = prefs.edit().putInt(KEY_KEY_HEIGHT, value.coerceIn(40, 76)).apply()

    /** フリック判定距離(dp)。10〜42 の範囲。 */
    var flickThresholdDp: Int
        get() = prefs.getInt(KEY_FLICK_THRESHOLD, DEFAULT_FLICK_THRESHOLD_DP)
        set(value) = prefs.edit().putInt(KEY_FLICK_THRESHOLD, value.coerceIn(10, 42)).apply()

    /** 片手モード。0=オフ 1=左寄せ 2=右寄せ。 */
    var oneHandedMode: Int
        get() = prefs.getInt(KEY_ONE_HANDED, 0)
        set(value) = prefs.edit().putInt(KEY_ONE_HANDED, value.coerceIn(0, 2)).apply()

    /** かな漢字変換(Mozc)を使うかどうか。既定はオン(予測変換・漢字変換を最初から利用可能に)。 */
    var kanjiConversionEnabled: Boolean
        get() = prefs.getBoolean(KEY_KANJI_CONVERSION, true)
        set(value) = prefs.edit().putBoolean(KEY_KANJI_CONVERSION, value).apply()
}
