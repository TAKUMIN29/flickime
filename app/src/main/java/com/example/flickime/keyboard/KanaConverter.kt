package com.example.flickime.keyboard

/**
 * ひらがな → カタカナ → 半角カタカナ の変換キー用ロジック。
 *
 * かな漢字変換エンジンを持たないため、直前に入力した語をそのまま
 * 表記だけ切り替える簡易変換（フィーチャーフォンの「変換」キー相当）。
 */
object KanaConverter {

    /**
     * 半角の英数字・記号（U+0021〜U+007E）を全角（U+FF01〜U+FF5E）にする。
     * この2つはコードポイントが 0xFEE0 ずれているだけ。半角スペースだけは
     * 対応する全角が離れた位置（U+3000）にあるので個別に扱う。
     */
    fun toFullWidthAscii(s: String): String = buildString {
        for (c in s) {
            append(
                when (c) {
                    ' ' -> '　'
                    in '!'..'~' -> c + 0xFEE0
                    else -> c
                },
            )
        }
    }

    /** ひらがな（U+3041〜U+3096）はカタカナ（U+30A1〜U+30F6）とコードポイントが 0x60 ずれているだけ。 */
    fun toKatakana(s: String): String = buildString {
        for (c in s) {
            append(if (c in 'ぁ'..'ゖ') c + 0x60 else c)
        }
    }

    /** 全角カタカナ・約物 → 半角カタカナ。濁点/半濁点は2文字（結合しない独立の記号）に展開する。 */
    fun toHalfWidthKatakana(s: String): String = buildString {
        for (c in s) {
            append(HALF_WIDTH_MAP[c] ?: c.toString())
        }
    }

    private val HALF_WIDTH_MAP: Map<Char, String> = buildMap {
        val plain = listOf(
            'ア' to "ｱ", 'イ' to "ｲ", 'ウ' to "ｳ", 'エ' to "ｴ", 'オ' to "ｵ",
            'カ' to "ｶ", 'キ' to "ｷ", 'ク' to "ｸ", 'ケ' to "ｹ", 'コ' to "ｺ",
            'サ' to "ｻ", 'シ' to "ｼ", 'ス' to "ｽ", 'セ' to "ｾ", 'ソ' to "ｿ",
            'タ' to "ﾀ", 'チ' to "ﾁ", 'ツ' to "ﾂ", 'テ' to "ﾃ", 'ト' to "ﾄ",
            'ナ' to "ﾅ", 'ニ' to "ﾆ", 'ヌ' to "ﾇ", 'ネ' to "ﾈ", 'ノ' to "ﾉ",
            'ハ' to "ﾊ", 'ヒ' to "ﾋ", 'フ' to "ﾌ", 'ヘ' to "ﾍ", 'ホ' to "ﾎ",
            'マ' to "ﾏ", 'ミ' to "ﾐ", 'ム' to "ﾑ", 'メ' to "ﾒ", 'モ' to "ﾓ",
            'ヤ' to "ﾔ", 'ユ' to "ﾕ", 'ヨ' to "ﾖ",
            'ラ' to "ﾗ", 'リ' to "ﾘ", 'ル' to "ﾙ", 'レ' to "ﾚ", 'ロ' to "ﾛ",
            'ワ' to "ﾜ", 'ヲ' to "ｦ", 'ン' to "ﾝ",
            'ー' to "ｰ",
            'ァ' to "ｧ", 'ィ' to "ｨ", 'ゥ' to "ｩ", 'ェ' to "ｪ", 'ォ' to "ｫ",
            'ッ' to "ｯ", 'ャ' to "ｬ", 'ュ' to "ｭ", 'ョ' to "ｮ",
            '、' to "､", '。' to "｡", '「' to "｢", '」' to "｣", '・' to "･",
        )
        for ((full, half) in plain) put(full, half)

        val voiced = listOf(
            'ガ' to "ｶ", 'ギ' to "ｷ", 'グ' to "ｸ", 'ゲ' to "ｹ", 'ゴ' to "ｺ",
            'ザ' to "ｻ", 'ジ' to "ｼ", 'ズ' to "ｽ", 'ゼ' to "ｾ", 'ゾ' to "ｿ",
            'ダ' to "ﾀ", 'ヂ' to "ﾁ", 'ヅ' to "ﾂ", 'デ' to "ﾃ", 'ド' to "ﾄ",
            'バ' to "ﾊ", 'ビ' to "ﾋ", 'ブ' to "ﾌ", 'ベ' to "ﾍ", 'ボ' to "ﾎ",
            'ヴ' to "ｳ",
        )
        for ((full, base) in voiced) put(full, base + "ﾞ")

        val semiVoiced = listOf(
            'パ' to "ﾊ", 'ピ' to "ﾋ", 'プ' to "ﾌ", 'ペ' to "ﾍ", 'ポ' to "ﾎ",
        )
        for ((full, base) in semiVoiced) put(full, base + "ﾟ")
    }
}
