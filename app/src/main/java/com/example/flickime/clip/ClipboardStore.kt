package com.example.flickime.clip

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ClipItem(
    val text: String,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * クリップボード履歴の保存先。
 *
 * 端末内（アプリ専用ディレクトリ）の JSON ファイルにのみ保存し、外部には一切送らない。
 * 固定(pinned)された項目は上限を超えても消えない。
 */
class ClipboardStore private constructor(context: Context) {

    companion object {
        private const val TAG = "ClipboardStore"
        private const val FILE_NAME = "clips.json"
        private const val MAX_UNPINNED = 100

        /** 1件あたりの保存上限。極端に長いテキストで肥大化させない。 */
        private const val MAX_TEXT_LENGTH = 5000

        @Volatile
        private var instance: ClipboardStore? = null

        fun get(context: Context): ClipboardStore =
            instance ?: synchronized(this) {
                instance ?: ClipboardStore(context.applicationContext).also { instance = it }
            }
    }

    private val file = File(context.filesDir, FILE_NAME)
    private val items = mutableListOf<ClipItem>()

    init {
        load()
    }

    @Synchronized
    fun all(): List<ClipItem> = items.toList()

    @Synchronized
    fun isEmpty(): Boolean = items.isEmpty()

    /**
     * 履歴に追加する。同じテキストが既にあれば先頭へ移動するだけ。
     */
    @Synchronized
    fun add(rawText: String) {
        val text = rawText.take(MAX_TEXT_LENGTH)
        if (text.isBlank()) return

        val existing = items.indexOfFirst { it.text == text }
        if (existing >= 0) {
            val item = items.removeAt(existing)
            items.add(0, item.copy(createdAt = System.currentTimeMillis()))
        } else {
            items.add(0, ClipItem(text))
        }
        trim()
        save()
    }

    @Synchronized
    fun remove(text: String) {
        if (items.removeAll { it.text == text }) save()
    }

    @Synchronized
    fun togglePin(text: String) {
        val index = items.indexOfFirst { it.text == text }
        if (index < 0) return
        val item = items[index]
        items[index] = item.copy(pinned = !item.pinned)
        // 固定した項目は先頭にまとめる
        items.sortWith(compareByDescending<ClipItem> { it.pinned }.thenByDescending { it.createdAt })
        save()
    }

    /** 固定していない項目をすべて削除する。 */
    @Synchronized
    fun clearUnpinned() {
        if (items.removeAll { !it.pinned }) save()
    }

    @Synchronized
    fun clearAll() {
        items.clear()
        save()
    }

    private fun trim() {
        var unpinned = 0
        val iterator = items.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.pinned) continue
            unpinned++
            if (unpinned > MAX_UNPINNED) iterator.remove()
        }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val text = obj.optString("text")
                if (text.isEmpty()) continue
                items.add(
                    ClipItem(
                        text = text,
                        pinned = obj.optBoolean("pinned", false),
                        createdAt = obj.optLong("createdAt", 0L),
                    ),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "履歴の読み込みに失敗したため初期化します", e)
            items.clear()
        }
    }

    private fun save() {
        try {
            val array = JSONArray()
            for (item in items) {
                array.put(
                    JSONObject()
                        .put("text", item.text)
                        .put("pinned", item.pinned)
                        .put("createdAt", item.createdAt),
                )
            }
            file.writeText(array.toString())
        } catch (e: Exception) {
            Log.w(TAG, "履歴の保存に失敗しました", e)
        }
    }
}
