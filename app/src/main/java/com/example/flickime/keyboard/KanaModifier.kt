package com.example.flickime.keyboard

/**
 * 「小゛゜」キーの変換ロジック。
 *
 * - [cycle]      : タップするたび 清音 → 濁音 → 半濁音（→ 小文字） と巡回する
 * - [dakuten]    : 濁点をトグル
 * - [handakuten] : 半濁点をトグル
 * - [small]      : 小文字をトグル
 */
object KanaModifier {

    /** 巡回の順序。各文字は次の文字へ、末尾は先頭へ戻る。 */
    private val CHAINS = listOf(
        "あぁ", "いぃ", "うぅゔ", "えぇ", "おぉ",
        "かが", "きぎ", "くぐ", "けげ", "こご",
        "さざ", "しじ", "すず", "せぜ", "そぞ",
        "ただ", "ちぢ", "つっづ", "てで", "とど",
        "はばぱ", "ひびぴ", "ふぶぷ", "へべぺ", "ほぼぽ",
        "やゃ", "ゆゅ", "よょ",
        "わゎ",
    )

    /** 清音と濁音の対。2文字ずつ並べる。 */
    private const val DAKUTEN_PAIRS =
        "かがきぎくぐけげこご" +
            "さざしじすずせぜそぞ" +
            "ただちぢつづてでとど" +
            "はばひびふぶへべほぼ" +
            "うゔ"

    /** 清音と半濁音の対。 */
    private const val HANDAKUTEN_PAIRS = "はぱひぴふぷへぺほぽ"

    /** 通常文字と小文字の対。 */
    private const val SMALL_PAIRS = "あぁいぃうぅえぇおぉつっやゃゆゅよょわゎ"

    private val cycleMap: Map<Char, Char> = HashMap<Char, Char>().apply {
        for (chain in CHAINS) {
            for (i in chain.indices) {
                put(chain[i], chain[(i + 1) % chain.length])
            }
        }
    }

    private fun pairsToMap(pairs: String): Map<Char, Char> = HashMap<Char, Char>().apply {
        var i = 0
        while (i + 1 < pairs.length) {
            put(pairs[i], pairs[i + 1])
            i += 2
        }
    }

    private fun invert(map: Map<Char, Char>): Map<Char, Char> =
        map.entries.associate { (k, v) -> v to k }

    private val dakutenMap = pairsToMap(DAKUTEN_PAIRS)
    private val dakutenReverse = invert(dakutenMap)
    private val handakutenMap = pairsToMap(HANDAKUTEN_PAIRS)
    private val handakutenReverse = invert(handakutenMap)
    private val smallMap = pairsToMap(SMALL_PAIRS)
    private val smallReverse = invert(smallMap)

    /** この文字に対して「小゛゜」キーが何らかの効果を持つか。 */
    fun isModifiable(c: Char): Boolean =
        cycleMap.containsKey(c) ||
            dakutenReverse.containsKey(c) ||
            handakutenReverse.containsKey(c) ||
            smallReverse.containsKey(c)

    fun cycle(c: Char): Char? = cycleMap[c]

    fun dakuten(c: Char): Char? = dakutenMap[c] ?: dakutenReverse[c]

    fun handakuten(c: Char): Char? = handakutenMap[c] ?: handakutenReverse[c]

    fun small(c: Char): Char? = smallMap[c] ?: smallReverse[c]

    /** 英字の大文字・小文字トグル。 */
    fun toggleCase(c: Char): Char? = when {
        c.isLowerCase() -> c.uppercaseChar()
        c.isUpperCase() -> c.lowercaseChar()
        else -> null
    }

    /**
     * フリック方向に応じた変換を適用する。適用できなければ null。
     */
    fun apply(c: Char, flick: Flick): Char? = when (flick) {
        Flick.CENTER -> cycle(c)
        Flick.LEFT -> dakuten(c)
        Flick.RIGHT -> handakuten(c)
        Flick.UP, Flick.DOWN -> small(c)
    }
}
