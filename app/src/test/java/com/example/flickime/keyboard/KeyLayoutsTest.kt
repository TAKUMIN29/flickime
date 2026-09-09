package com.example.flickime.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyLayoutsTest {

    @Test
    fun `あAキーは ひらがな 半角英字 全角英字 を巡回する`() {
        var mode = KeyLayouts.Mode.KANA
        mode = KeyLayouts.nextMode(mode)
        assertEquals(KeyLayouts.Mode.ALPHABET, mode)
        mode = KeyLayouts.nextMode(mode)
        assertEquals(KeyLayouts.Mode.ALPHABET_FULL, mode)
        mode = KeyLayouts.nextMode(mode)
        assertEquals(KeyLayouts.Mode.KANA, mode)
    }

    @Test
    fun `数字や記号からはひらがなへ戻る`() {
        assertEquals(KeyLayouts.Mode.KANA, KeyLayouts.nextMode(KeyLayouts.Mode.NUMBER))
        assertEquals(KeyLayouts.Mode.KANA, KeyLayouts.nextMode(KeyLayouts.Mode.NUMBER_FULL))
        assertEquals(KeyLayouts.Mode.KANA, KeyLayouts.nextMode(KeyLayouts.Mode.SYMBOLS))
    }

    @Test
    fun `123キーは半角数字と全角数字を行き来する`() {
        assertEquals(KeyLayouts.Mode.NUMBER, KeyLayouts.nextNumberMode(KeyLayouts.Mode.KANA))
        assertEquals(KeyLayouts.Mode.NUMBER_FULL, KeyLayouts.nextNumberMode(KeyLayouts.Mode.NUMBER))
        assertEquals(KeyLayouts.Mode.NUMBER, KeyLayouts.nextNumberMode(KeyLayouts.Mode.NUMBER_FULL))
    }

    @Test
    fun `全角レイアウトは文字キーの出力だけを全角にする`() {
        val half = KeyLayouts.NUMBER
        val full = KeyLayouts.NUMBER_FULL
        assertEquals(half.size, full.size)

        for ((halfRow, fullRow) in half.zip(full)) {
            for ((halfKey, fullKey) in halfRow.zip(fullRow)) {
                assertEquals(halfKey.type, fullKey.type)
                if (halfKey.type == KeyType.CHAR) {
                    assertEquals(
                        halfKey.chars.map { KanaConverter.toFullWidthAscii(it) },
                        fullKey.chars,
                    )
                } else {
                    // 機能キーは半角のまま流用する
                    assertEquals(halfKey, fullKey)
                }
            }
        }
    }

    @Test
    fun `全角数字レイアウトは全角の数字を出す`() {
        val one = KeyLayouts.NUMBER_FULL[0][1]
        assertEquals("１", one.output(Flick.CENTER))
    }

    @Test
    fun `全角英字レイアウトは全角の英字を出す`() {
        val abc = KeyLayouts.ALPHABET_FULL[0][2]
        assertEquals("ａ", abc.output(Flick.CENTER))
        assertEquals("ｂ", abc.output(Flick.LEFT))
    }

    @Test
    fun `どのレイアウトも4行5列で左右にカーソルキーがある`() {
        for (mode in KeyLayouts.Mode.entries) {
            val rows = KeyLayouts.of(mode)
            assertEquals("$mode の行数", 4, rows.size)
            assertTrue("$mode の列数", rows.all { it.size == 5 })
            assertEquals("$mode の左カーソル", KeyType.CURSOR_LEFT, rows[1][0].type)
            assertEquals("$mode の右カーソル", KeyType.CURSOR_RIGHT, rows[1][4].type)
            assertEquals("$mode のモードキー", KeyType.MODE, rows[3][0].type)
            assertEquals("$mode の数字キー", KeyType.NUM_OR_KANA, rows[2][0].type)
        }
    }
}
