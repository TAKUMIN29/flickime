package com.example.flickime.dict

import org.junit.Assert.assertEquals
import org.junit.Test

class UserDictionaryTest {

    private val entries = listOf(
        DictEntry("あんど", "Android"),
        DictEntry("あん", "餡"),
        DictEntry("あんどろいど", "Android端末"),
        DictEntry("いぬ", "犬"),
    )

    @Test
    fun `前方一致した語だけ返す`() {
        assertEquals(listOf("犬"), UserDictionary.match(entries, "いぬ", 10))
        assertEquals(emptyList<String>(), UserDictionary.match(entries, "ねこ", 10))
    }

    @Test
    fun `読みが完全一致するものを先頭に出す`() {
        // 「あん」は「あんど」「あんどろいど」にも前方一致するが、完全一致が最優先
        assertEquals(listOf("餡", "Android", "Android端末"), UserDictionary.match(entries, "あん", 10))
    }

    @Test
    fun `完全一致がなければ読みの短い順に並べる`() {
        assertEquals(listOf("Android", "Android端末"), UserDictionary.match(entries, "あんど", 10))
    }

    @Test
    fun `limit で件数を絞る`() {
        assertEquals(listOf("餡"), UserDictionary.match(entries, "あん", 1))
    }

    @Test
    fun `空の読みでは何も返さない`() {
        assertEquals(emptyList<String>(), UserDictionary.match(entries, "", 10))
    }

    @Test
    fun `同じ語が重複していたら1つにまとめる`() {
        val duplicated = entries + DictEntry("あんどろ", "Android")
        assertEquals(listOf("Android", "Android端末"), UserDictionary.match(duplicated, "あんど", 10))
    }
}
