package com.example.flickime.dict

import android.content.Context
import android.util.Log
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** ユーザー辞書の1件。[reading] は「読み」、[word] は変換後に入力される語。 */
data class DictEntry(val reading: String, val word: String)

/**
 * ユーザー辞書の保存先。
 *
 * Mozc の辞書へは登録せず、変換候補を出すときにこちらで前方一致した語を先頭に足す。
 * Mozc のユーザー辞書へ流し込む方式に比べて、登録した語が必ず候補に出ることを保証しやすい。
 * 端末内（アプリ専用ディレクトリ）の JSON にのみ保存し、外部には一切送らない。
 */
class UserDictionary private constructor(context: Context) {

    companion object {
        private const val TAG = "UserDictionary"
        private const val FILE_NAME = "user_dict.json"
        private const val MAX_ENTRIES = 2000

        /**
         * [reading] に前方一致する語を、読みが完全一致するものを先に、
         * 続けて読みの短い順に並べて返す。短い読みほど狙った語である可能性が高いため。
         */
        fun match(entries: List<DictEntry>, reading: String, limit: Int): List<String> {
            if (reading.isEmpty()) return emptyList()
            return entries
                .filter { it.reading.startsWith(reading) }
                .sortedWith(compareBy({ it.reading != reading }, { it.reading.length }))
                .map { it.word }
                .distinct()
                .take(limit)
        }

        @Volatile
        private var instance: UserDictionary? = null

        fun get(context: Context): UserDictionary =
            instance ?: synchronized(this) {
                instance ?: UserDictionary(context.applicationContext).also { instance = it }
            }
    }

    private val file = File(context.filesDir, FILE_NAME)
    private val entries = mutableListOf<DictEntry>()

    init {
        load()
    }

    @Synchronized
    fun all(): List<DictEntry> = entries.toList()

    /**
     * 登録する。同じ読みと語の組が既にあれば何もしない。
     * 読みが同じで語が違うものは、別の候補として共存させる。
     */
    @Synchronized
    fun add(reading: String, word: String): Boolean {
        val r = reading.trim()
        val w = word.trim()
        if (r.isEmpty() || w.isEmpty()) return false
        if (entries.any { it.reading == r && it.word == w }) return false
        entries.add(0, DictEntry(r, w))
        if (entries.size > MAX_ENTRIES) entries.subList(MAX_ENTRIES, entries.size).clear()
        save()
        return true
    }

    @Synchronized
    fun remove(entry: DictEntry) {
        if (entries.removeAll { it.reading == entry.reading && it.word == entry.word }) save()
    }

    @Synchronized
    fun clearAll() {
        entries.clear()
        save()
    }

    /** [reading] で始まる読みを持つ語を返す。並び順は [match] を参照。 */
    @Synchronized
    fun lookup(reading: String, limit: Int): List<String> = match(entries, reading, limit)

    private fun load() {
        if (!file.exists()) return
        try {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val reading = obj.optString("reading")
                val word = obj.optString("word")
                if (reading.isEmpty() || word.isEmpty()) continue
                entries.add(DictEntry(reading, word))
            }
        } catch (e: Exception) {
            Log.w(TAG, "ユーザー辞書の読み込みに失敗したため初期化します", e)
            entries.clear()
        }
    }

    private fun save() {
        try {
            val array = JSONArray()
            for (entry in entries) {
                array.put(JSONObject().put("reading", entry.reading).put("word", entry.word))
            }
            file.writeText(array.toString())
        } catch (e: Exception) {
            Log.w(TAG, "ユーザー辞書の保存に失敗しました", e)
        }
    }
}
