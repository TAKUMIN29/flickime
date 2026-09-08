package com.example.flickime.keyboard

/** フリック方向。CENTER はタップ（フリックなし）。 */
enum class Flick {
    CENTER, LEFT, UP, RIGHT, DOWN;

    /** KeySpec.chars 内でのインデックス。 */
    val index: Int get() = ordinal
}

enum class KeyType {
    /** 方向に応じて文字を出すキー。 */
    CHAR,

    /** 直前の文字に濁点/半濁点/小文字を付けるキー。 */
    MODIFIER,

    BACKSPACE,
    SPACE,
    ENTER,

    /** 左右フリックでカーソル移動。 */
    CURSOR,

    /** かな → 英字 → 数字 の切り替え。 */
    MODE,

    /** 記号パレットの表示切り替え。 */
    SYMBOL,

    /** 英字モードでの大文字小文字切り替え。 */
    CASE,

    /** 他の IME へ切り替え。 */
    IME_SWITCH,

    /** 設定画面を開く。 */
    SETTINGS,

    /** かな漢字変換(Mozc)の候補がある間だけ CURSOR キーの代わりに表示される変換キー。 */
    CONVERT,
}

/**
 * フリックの判定が際どかったときの「次点」候補。
 *
 * 指を離した位置から本命ではないと判定された方向でも、境界付近であれば
 * 一定の確率で「本当はそちらを狙っていたかもしれない」とみなし、
 * かな漢字変換エンジンへの確率付きヒントとして使う（誤フリックの許容）。
 */
data class FlickCandidate(val text: String, val probability: Float)

/**
 * キー1つ分の定義。
 *
 * [chars] は [Flick.index] の順（CENTER, LEFT, UP, RIGHT, DOWN）で並べる。
 * 割り当てのない方向は空文字にしておく。
 */
data class KeySpec(
    val type: KeyType,
    val label: String,
    val chars: List<String> = emptyList(),
    /** 機能キーは背景色を変えるため区別する。 */
    val isFunction: Boolean = type != KeyType.CHAR,
) {
    fun output(flick: Flick): String? = chars.getOrNull(flick.index)?.takeIf { it.isNotEmpty() }

    /** フリック候補（吹き出し）を出すべきキーかどうか。 */
    fun hasFlickVariants(): Boolean = chars.count { it.isNotEmpty() } > 1

    /**
     * 同じキーを連打したときの文字送り。
     * CENTER 位置から数えて [tapCount] 番目の（空でない）文字を返す。
     */
    fun cycled(tapCount: Int): String? {
        val available = chars.filter { it.isNotEmpty() }
        if (available.isEmpty()) return null
        return available[tapCount % available.size]
    }
}
