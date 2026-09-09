package com.example.flickime.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KanaConverterTest {

    @Test
    fun `toKatakana converts the whole hiragana range including vu and small kana`() {
        assertEquals("アイウエオ", KanaConverter.toKatakana("あいうえお"))
        assertEquals("ガッコウ", KanaConverter.toKatakana("がっこう"))
        assertEquals("ヴ", KanaConverter.toKatakana("ゔ"))
    }

    @Test
    fun `toKatakana leaves non-hiragana characters untouched`() {
        assertEquals("、。ー123abc", KanaConverter.toKatakana("、。ー123abc"))
    }

    @Test
    fun `toHalfWidthKatakana expands voiced and semi-voiced sounds into two characters`() {
        assertEquals("ｶﾞｯｺｳ", KanaConverter.toHalfWidthKatakana("ガッコウ"))
        assertEquals("ﾊﾟﾋﾟﾌﾟﾍﾟﾎﾟ", KanaConverter.toHalfWidthKatakana("パピプペポ"))
        assertEquals("ｳﾞ", KanaConverter.toHalfWidthKatakana("ヴ"))
    }

    @Test
    fun `toHalfWidthKatakana maps punctuation used on the kana layout`() {
        assertEquals("｢｣､｡･", KanaConverter.toHalfWidthKatakana("「」、。・"))
    }

    @Test
    fun `full conversion chain matches a typical word`() {
        val hiragana = "がっこう"
        val katakana = KanaConverter.toKatakana(hiragana)
        val hankaku = KanaConverter.toHalfWidthKatakana(katakana)

        assertEquals("ガッコウ", katakana)
        assertEquals("ｶﾞｯｺｳ", hankaku)
    }

    @Test
    fun `半角英数字と記号を全角にする`() {
        assertEquals("ＡＢＣ", KanaConverter.toFullWidthAscii("ABC"))
        assertEquals("１２３", KanaConverter.toFullWidthAscii("123"))
        assertEquals("＠＃＆", KanaConverter.toFullWidthAscii("@#&"))
    }

    @Test
    fun `半角スペースは全角スペースにする`() {
        assertEquals("あ　い", KanaConverter.toFullWidthAscii("あ い"))
    }

    @Test
    fun `全角の文字はそのまま返す`() {
        assertEquals("あＡ１", KanaConverter.toFullWidthAscii("あＡ１"))
    }
}
