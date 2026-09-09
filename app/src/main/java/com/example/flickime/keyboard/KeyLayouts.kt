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
    /**
     * 左列の下から2番目に置く動的キー。上フリックで他の IME へ切り替える。
     * タップ時の意味はラベルごとに [NUMBER_KEY] / [KANA_KEY] / [KATAKANA_KEY] を参照。
     */
    private fun numOrKana(label: String) =
        KeySpec(KeyType.NUM_OR_KANA, label, listOf(label, "", "⌨", "", ""))

    /** 数字レイアウトへ切り替える。 */
    val NUMBER_KEY: KeySpec = numOrKana("123")

    /** 数字レイアウトからかなへ戻る。 */
    val KANA_KEY: KeySpec = numOrKana("かな")

    /** 入力中の未確定文字列をカタカナに変換する。 */
    val KATAKANA_KEY: KeySpec = numOrKana("カナ")
    // 上フリックで片手モード(オフ/左寄せ/右寄せ)を切り替える
    private val SETTINGS = KeySpec(KeyType.SETTINGS, "設定", listOf("設定", "", "片手", "", ""))
    // 左フリックで単語単位の削除
    private val BACKSPACE = KeySpec(KeyType.BACKSPACE, "削除", listOf("削除", "単語", "", "", ""))
    private val SPACE = KeySpec(KeyType.SPACE, "空白")

    // かなモード専用。上フリックで直前に入力した語をカタカナ/半角カタカナに変換する
    private val SPACE_KANA = KeySpec(KeyType.SPACE, "空白", listOf("空白", "", "変換", "", ""))
    private val ENTER = KeySpec(KeyType.ENTER, "改行")
    private val CURSOR = KeySpec(KeyType.CURSOR, "←→", listOf("", "←", "", "→", ""))
    // 濁点キーは見た目上は文字キーと同じ扱いにする
    private val MODIFIER =
        KeySpec(KeyType.MODIFIER, "小゛゜", listOf("小", "゛", "小", "゜", "小"), isFunction = false)
    private val CASE = KeySpec(KeyType.CASE, "a/A")

    /**
     * かな漢字変換(Mozc)の候補が出ている間だけ CURSOR キーの位置に差し替える変換キー。
     * FlickImeService が変換中かどうかに応じてレイアウトを組み替える際に使う。
     */
    // 左右フリックで文節の移動、上下フリックで文節の区切りの伸縮
    val CONVERT: KeySpec =
        KeySpec(KeyType.CONVERT, "変換", listOf("変換", "◀文節", "長く", "文節▶", "短く"))

    /** ひらがな（フリック）。 */
    val KANA: List<List<KeySpec>> = listOf(
        listOf(MODE, ch("あ", "い", "う", "え", "お"), ch("か", "き", "く", "け", "こ"), ch("さ", "し", "す", "せ", "そ"), BACKSPACE),
        listOf(SYMBOL, ch("た", "ち", "つ", "て", "と"), ch("な", "に", "ぬ", "ね", "の"), ch("は", "ひ", "ふ", "へ", "ほ"), SPACE_KANA),
        listOf(NUMBER_KEY, ch("ま", "み", "む", "め", "も"), ch("や", "「", "ゆ", "」", "よ"), ch("ら", "り", "る", "れ", "ろ"), CURSOR),
        listOf(SETTINGS, MODIFIER, ch("わ", "を", "ん", "ー", "〜"), ch("、", "。", "？", "！", "…"), ENTER),
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
            NUMBER_KEY,
            ch("p", "q", "r", "s", "7", label = "PQRS"),
            ch("t", "u", "v", "T", "8", label = "TUV"),
            ch("w", "x", "y", "z", "9", label = "WXYZ"),
            CURSOR,
        ),
        listOf(
            SETTINGS,
            CASE,
            ch("'", "\"", "(", ")", ":", label = "'\"()"),
            ch(".", ",", "?", "!", "-", label = ".,?!"),
            ENTER,
        ),
    )

    /** 数字。 */
    val NUMBER: List<List<KeySpec>> = listOf(
        listOf(MODE, ch("1", "☆", "♪", "→", "-"), ch("2", "¥", "$", "€", "+"), ch("3", "%", "°", "#", "*"), BACKSPACE),
        listOf(SYMBOL, ch("4", "○", "*", "・", "/"), ch("5", "+", "×", "÷", "="), ch("6", "<", "=", ">", "^"), SPACE),
        listOf(NUMBER_KEY, ch("7", "「", "」", ":", "~"), ch("8", "〒", "々", "〆", "@"), ch("9", "^", "|", "\\", "_"), CURSOR),
        listOf(SETTINGS, ch("(", ")", "[", "]", "{"), ch("0", "〜", "…", "‥", "、"), ch(".", ",", "-", "/", ":"), ENTER),
    )

    /** 記号パレット。 */
    val SYMBOLS: List<List<KeySpec>> = listOf(
        listOf(MODE, ch("、", "。", "！", "？", "…"), ch("「", "」", "『", "』", "・"), ch("(", ")", "[", "]", "{"), BACKSPACE),
        listOf(SYMBOL, ch("@", "#", "$", "%", "&"), ch("+", "-", "*", "/", "="), ch("<", ">", "≦", "≧", "≠"), SPACE),
        listOf(NUMBER_KEY, ch("^", "_", "|", "\\", "~"), ch("¥", "€", "£", "¢", "°"), ch("♪", "☆", "♡", "※", "→"), CURSOR),
        listOf(SETTINGS, ch("○", "●", "△", "▲", "□"), ch("■", "◇", "◆", "★", "×"), ch(":", ";", "'", "\"", "`"), ENTER),
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
