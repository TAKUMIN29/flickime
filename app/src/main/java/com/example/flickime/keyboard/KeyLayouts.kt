package com.example.flickime.keyboard

/**
 * 4行 × 5列 のケータイ配列。
 *
 *   [機能] [かな] [かな] [かな] [機能]
 *
 * 中央3列がフリック入力の 12 キー、左右端が機能キー。
 */
object KeyLayouts {

    private fun ch(
        center: String,
        left: String = "",
        up: String = "",
        right: String = "",
        down: String = "",
        label: String = center,
    ) = KeySpec(KeyType.CHAR, label, listOf(center, left, up, right, down))

    // 上フリックでかな漢字変換(Mozc)のオン/オフを切り替える
    private val MODE = KeySpec(KeyType.MODE, "あ/A/1", listOf("あ/A/1", "", "変換", "", ""))
    private val SYMBOL = KeySpec(KeyType.SYMBOL, "記号")
    private val IME_SWITCH = KeySpec(KeyType.IME_SWITCH, "⌨")
    // 上フリックで片手モード(オフ/左寄せ/右寄せ)を切り替える
    private val SETTINGS = KeySpec(KeyType.SETTINGS, "設定", listOf("設定", "", "片手", "", ""))
    private val BACKSPACE = KeySpec(KeyType.BACKSPACE, "削除")
    private val SPACE = KeySpec(KeyType.SPACE, "空白")

    // かなモード専用。上フリックで直前に入力した語をカタカナ/半角カタカナに変換する
    private val SPACE_KANA = KeySpec(KeyType.SPACE, "空白", listOf("空白", "", "変換", "", ""))
    private val ENTER = KeySpec(KeyType.ENTER, "改行")
    private val CURSOR = KeySpec(KeyType.CURSOR, "←→", listOf("", "←", "", "→", ""))
    // 濁点キーは見た目上は文字キーと同じ扱いにする
    private val MODIFIER =
        KeySpec(KeyType.MODIFIER, "小゛゜", listOf("小", "゛", "小", "゜", "小"), isFunction = false)
    private val CASE = KeySpec(KeyType.CASE, "a/A")

    /** ひらがな（フリック）。 */
    val KANA: List<List<KeySpec>> = listOf(
        listOf(MODE, ch("あ", "い", "う", "え", "お"), ch("か", "き", "く", "け", "こ"), ch("さ", "し", "す", "せ", "そ"), BACKSPACE),
        listOf(SYMBOL, ch("た", "ち", "つ", "て", "と"), ch("な", "に", "ぬ", "ね", "の"), ch("は", "ひ", "ふ", "へ", "ほ"), SPACE_KANA),
        listOf(IME_SWITCH, ch("ま", "み", "む", "め", "も"), ch("や", "「", "ゆ", "」", "よ"), ch("ら", "り", "る", "れ", "ろ"), ENTER),
        listOf(SETTINGS, MODIFIER, ch("わ", "を", "ん", "ー", "〜"), ch("、", "。", "？", "！", "…"), CURSOR),
    )

    /** 英字（フリック）。 */
    val ALPHABET: List<List<KeySpec>> = listOf(
        listOf(
            MODE,
            ch("@", "#", "&", "_", "1", label = "@#&"),
            ch("a", "b", "c", "A", "2", label = "ABC"),
            ch("d", "e", "f", "D", "3", label = "DEF"),
            BACKSPACE,
        ),
        listOf(
            SYMBOL,
            ch("g", "h", "i", "G", "4", label = "GHI"),
            ch("j", "k", "l", "J", "5", label = "JKL"),
            ch("m", "n", "o", "M", "6", label = "MNO"),
            SPACE,
        ),
        listOf(
            IME_SWITCH,
            ch("p", "q", "r", "s", "7", label = "PQRS"),
            ch("t", "u", "v", "T", "8", label = "TUV"),
            ch("w", "x", "y", "z", "9", label = "WXYZ"),
            ENTER,
        ),
        listOf(
            SETTINGS,
            CASE,
            ch("'", "\"", "(", ")", ":", label = "'\"()"),
            ch(".", ",", "?", "!", "-", label = ".,?!"),
            CURSOR,
        ),
    )

    /** 数字。 */
    val NUMBER: List<List<KeySpec>> = listOf(
        listOf(MODE, ch("1", "☆", "♪", "→", "-"), ch("2", "¥", "$", "€", "+"), ch("3", "%", "°", "#", "*"), BACKSPACE),
        listOf(SYMBOL, ch("4", "○", "*", "・", "/"), ch("5", "+", "×", "÷", "="), ch("6", "<", "=", ">", "^"), SPACE),
        listOf(IME_SWITCH, ch("7", "「", "」", ":", "~"), ch("8", "〒", "々", "〆", "@"), ch("9", "^", "|", "\\", "_"), ENTER),
        listOf(SETTINGS, ch("(", ")", "[", "]", "{"), ch("0", "〜", "…", "‥", "、"), ch(".", ",", "-", "/", ":"), CURSOR),
    )

    /** 記号パレット。 */
    val SYMBOLS: List<List<KeySpec>> = listOf(
        listOf(MODE, ch("、", "。", "！", "？", "…"), ch("「", "」", "『", "』", "・"), ch("(", ")", "[", "]", "{"), BACKSPACE),
        listOf(SYMBOL, ch("@", "#", "$", "%", "&"), ch("+", "-", "*", "/", "="), ch("<", ">", "≦", "≧", "≠"), SPACE),
        listOf(IME_SWITCH, ch("^", "_", "|", "\\", "~"), ch("¥", "€", "£", "¢", "°"), ch("♪", "☆", "♡", "※", "→"), ENTER),
        listOf(SETTINGS, ch("○", "●", "△", "▲", "□"), ch("■", "◇", "◆", "★", "×"), ch(":", ";", "'", "\"", "`"), CURSOR),
    )

    enum class Mode { KANA, ALPHABET, NUMBER, SYMBOLS }

    fun of(mode: Mode): List<List<KeySpec>> = when (mode) {
        Mode.KANA -> KANA
        Mode.ALPHABET -> ALPHABET
        Mode.NUMBER -> NUMBER
        Mode.SYMBOLS -> SYMBOLS
    }

    /** MODE キーを押したときの遷移（記号パレットからは かな に戻す）。 */
    fun nextMode(mode: Mode): Mode = when (mode) {
        Mode.KANA -> Mode.ALPHABET
        Mode.ALPHABET -> Mode.NUMBER
        Mode.NUMBER -> Mode.KANA
        Mode.SYMBOLS -> Mode.KANA
    }
}
