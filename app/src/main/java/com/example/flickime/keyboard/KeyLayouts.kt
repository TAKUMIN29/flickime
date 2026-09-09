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

    // ひらがな → 半角英字 → 全角英字 と切り替える。上フリックでかな漢字変換(Mozc)のオン/オフ
    private val MODE = KeySpec(KeyType.MODE, "あ/A/Ａ", listOf("あ/A/Ａ", "", "変換", "", ""))

    // 上フリックで片手モード(オフ/左寄せ/右寄せ)を切り替える。設定はツールバーの「設定」から開く
    private val SYMBOL = KeySpec(KeyType.SYMBOL, "記号", listOf("記号", "", "片手", "", ""))

    /**
     * 左列の下から2番目に置く動的キー。上フリックで他の IME へ切り替える。
     * タップ時の意味はラベルごとに [NUMBER_KEY] / [NUMBER_FULL_KEY] / [KATAKANA_KEY] を参照。
     */
    private fun numOrKana(label: String) =
        KeySpec(KeyType.NUM_OR_KANA, label, listOf(label, "", "⌨", "", ""))

    /** 半角数字のレイアウトへ切り替える。 */
    val NUMBER_KEY: KeySpec = numOrKana("123")

    /** 全角数字のレイアウトへ切り替える。 */
    val NUMBER_FULL_KEY: KeySpec = numOrKana("１２３")

    /** 入力中の未確定文字列をカタカナに変換する。 */
    val KATAKANA_KEY: KeySpec = numOrKana("カナ")

    // 左フリックで単語単位の削除
    private val BACKSPACE = KeySpec(KeyType.BACKSPACE, "削除", listOf("削除", "単語", "", "", ""))
    private val SPACE = KeySpec(KeyType.SPACE, "空白")

    // かなモード専用。上フリックで直前に入力した語をカタカナ/半角カタカナに変換する
    private val SPACE_KANA = KeySpec(KeyType.SPACE, "空白", listOf("空白", "", "変換", "", ""))
    private val ENTER = KeySpec(KeyType.ENTER, "改行")

    private val CURSOR_LEFT = KeySpec(KeyType.CURSOR_LEFT, "←")
    private val CURSOR_RIGHT = KeySpec(KeyType.CURSOR_RIGHT, "→")

    // 濁点キーは見た目上は文字キーと同じ扱いにする
    private val MODIFIER =
        KeySpec(KeyType.MODIFIER, "小゛゜", listOf("小", "゛", "小", "゜", "小"), isFunction = false)
    private val CASE = KeySpec(KeyType.CASE, "a/A")

    /**
     * かな漢字変換(Mozc)の候補が出ている間だけ、空白キーの位置に差し替える変換キー。
     * FlickImeService が変換中かどうかに応じてレイアウトを組み替える際に使う。
     */
    // 左右フリックで文節の移動、上下フリックで文節の区切りの伸縮
    val CONVERT: KeySpec =
        KeySpec(KeyType.CONVERT, "変換", listOf("変換", "◀文節", "長く", "文節▶", "短く"))

    /**
     * ひらがな（フリック）。
     *
     * 左右の機能キーの並びは Simeji に合わせている。
     *   記号 / ← / 123 / あA   と   削除 / → / 空白 / 改行
     */
    val KANA: List<List<KeySpec>> = listOf(
        listOf(SYMBOL, ch("あ", "い", "う", "え", "お"), ch("か", "き", "く", "け", "こ"), ch("さ", "し", "す", "せ", "そ"), BACKSPACE),
        listOf(CURSOR_LEFT, ch("た", "ち", "つ", "て", "と"), ch("な", "に", "ぬ", "ね", "の"), ch("は", "ひ", "ふ", "へ", "ほ"), CURSOR_RIGHT),
        listOf(NUMBER_KEY, ch("ま", "み", "む", "め", "も"), ch("や", "「", "ゆ", "」", "よ"), ch("ら", "り", "る", "れ", "ろ"), SPACE_KANA),
        listOf(MODE, MODIFIER, ch("わ", "を", "ん", "ー", "〜"), ch("、", "。", "？", "！", "…"), ENTER),
    )

    /** 半角英字（フリック）。 */
    val ALPHABET: List<List<KeySpec>> = listOf(
        listOf(
            SYMBOL,
            ch("@", "#", "&", "_", "1", label = "@#&"),
            ch("a", "b", "c", "A", "2", label = "ABC"),
            ch("d", "e", "f", "D", "3", label = "DEF"),
            BACKSPACE,
        ),
        listOf(
            CURSOR_LEFT,
            ch("g", "h", "i", "G", "4", label = "GHI"),
            ch("j", "k", "l", "J", "5", label = "JKL"),
            ch("m", "n", "o", "M", "6", label = "MNO"),
            CURSOR_RIGHT,
        ),
        listOf(
            NUMBER_KEY,
            ch("p", "q", "r", "s", "7", label = "PQRS"),
            ch("t", "u", "v", "T", "8", label = "TUV"),
            ch("w", "x", "y", "z", "9", label = "WXYZ"),
            SPACE,
        ),
        listOf(
            MODE,
            CASE,
            ch("'", "\"", "(", ")", ":", label = "'\"()"),
            ch(".", ",", "?", "!", "-", label = ".,?!"),
            ENTER,
        ),
    )

    /** 半角数字。 */
    val NUMBER: List<List<KeySpec>> = listOf(
        listOf(SYMBOL, ch("1", "☆", "♪", "→", "-"), ch("2", "¥", "$", "€", "+"), ch("3", "%", "°", "#", "*"), BACKSPACE),
        listOf(CURSOR_LEFT, ch("4", "○", "*", "・", "/"), ch("5", "+", "×", "÷", "="), ch("6", "<", "=", ">", "^"), CURSOR_RIGHT),
        listOf(NUMBER_KEY, ch("7", "「", "」", ":", "~"), ch("8", "〒", "々", "〆", "@"), ch("9", "^", "|", "\\", "_"), SPACE),
        listOf(MODE, ch("(", ")", "[", "]", "{"), ch("0", "〜", "…", "‥", "、"), ch(".", ",", "-", "/", ":"), ENTER),
    )

    /** 記号パレット。 */
    val SYMBOLS: List<List<KeySpec>> = listOf(
        listOf(SYMBOL, ch("、", "。", "！", "？", "…"), ch("「", "」", "『", "』", "・"), ch("(", ")", "[", "]", "{"), BACKSPACE),
        listOf(CURSOR_LEFT, ch("@", "#", "$", "%", "&"), ch("+", "-", "*", "/", "="), ch("<", ">", "≦", "≧", "≠"), CURSOR_RIGHT),
        listOf(NUMBER_KEY, ch("^", "_", "|", "\\", "~"), ch("¥", "€", "£", "¢", "°"), ch("♪", "☆", "♡", "※", "→"), SPACE),
        listOf(MODE, ch("○", "●", "△", "▲", "□"), ch("■", "◇", "◆", "★", "×"), ch(":", ";", "'", "\"", "`"), ENTER),
    )

    /**
     * 半角の配列から全角版を作る。文字キーの出力とラベルだけを全角に置き換え、
     * 機能キーはそのまま使う。半角と全角で配列表を二重に持たずに済む。
     */
    private fun toFullWidth(rows: List<List<KeySpec>>): List<List<KeySpec>> =
        rows.map { row ->
            row.map { key ->
                if (key.type != KeyType.CHAR) {
                    key
                } else {
                    key.copy(
                        label = KanaConverter.toFullWidthAscii(key.label),
                        chars = key.chars.map { KanaConverter.toFullWidthAscii(it) },
                    )
                }
            }
        }

    /** 全角英字（フリック）。 */
    val ALPHABET_FULL: List<List<KeySpec>> = toFullWidth(ALPHABET)

    /** 全角数字。 */
    val NUMBER_FULL: List<List<KeySpec>> = toFullWidth(NUMBER)

    enum class Mode { KANA, ALPHABET, ALPHABET_FULL, NUMBER, NUMBER_FULL, SYMBOLS }

    fun of(mode: Mode): List<List<KeySpec>> = when (mode) {
        Mode.KANA -> KANA
        Mode.ALPHABET -> ALPHABET
        Mode.ALPHABET_FULL -> ALPHABET_FULL
        Mode.NUMBER -> NUMBER
        Mode.NUMBER_FULL -> NUMBER_FULL
        Mode.SYMBOLS -> SYMBOLS
    }

    /** 「あ/A/Ａ」キー。ひらがな → 半角英字 → 全角英字 と巡回し、数字や記号からはひらがなへ戻す。 */
    fun nextMode(mode: Mode): Mode = when (mode) {
        Mode.KANA -> Mode.ALPHABET
        Mode.ALPHABET -> Mode.ALPHABET_FULL
        Mode.ALPHABET_FULL -> Mode.KANA
        Mode.NUMBER, Mode.NUMBER_FULL, Mode.SYMBOLS -> Mode.KANA
    }

    /** 「123 / １２３」キー。半角数字と全角数字を行き来する。 */
    fun nextNumberMode(mode: Mode): Mode =
        if (mode == Mode.NUMBER) Mode.NUMBER_FULL else Mode.NUMBER
}
