package com.example.flickime.edit

import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * 未使用のメソッドは Mockito のモックへ委譲し、実際に EditHistory が呼ぶ
 * getTextBeforeCursor / commitText / deleteSurroundingText / batchEdit のみ
 * カーソル位置つきのテキストバッファで実装する。
 */
private class FakeInputConnection : InputConnection by mock(InputConnection::class.java) {
    val buffer = StringBuilder()
    var cursor = 0
        private set

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
        val start = (cursor - n).coerceAtLeast(0)
        return buffer.substring(start, cursor)
    }

    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        buffer.insert(cursor, text)
        cursor += text.length
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        val start = (cursor - beforeLength).coerceAtLeast(0)
        buffer.delete(start, cursor)
        cursor = start
        return true
    }

    override fun beginBatchEdit(): Boolean = true
    override fun endBatchEdit(): Boolean = true
}

class EditHistoryTest {

    @Test
    fun `consecutive inserts within the merge window collapse into one undo step`() {
        var time = 0L
        val history = EditHistory(clock = { time })
        val ic = FakeInputConnection()

        ic.commitText("あ", 1); history.recordInsert("あ")
        time += 100
        ic.commitText("い", 1); history.recordInsert("い")

        assertEquals("あい", ic.buffer.toString())
        assertTrue(history.undo(ic))
        assertEquals("", ic.buffer.toString())
        assertFalse(history.canUndo)
    }

    @Test
    fun `breakMerge splits inserts into separate undo steps`() {
        val history = EditHistory(clock = { 0L })
        val ic = FakeInputConnection()

        ic.commitText("あ", 1); history.recordInsert("あ")
        history.breakMerge()
        ic.commitText("い", 1); history.recordInsert("い")

        assertTrue(history.undo(ic))
        assertEquals("あ", ic.buffer.toString())
        assertTrue(history.undo(ic))
        assertEquals("", ic.buffer.toString())
    }

    @Test
    fun `inserts outside the merge window are not merged`() {
        var time = 0L
        val history = EditHistory(clock = { time })
        val ic = FakeInputConnection()

        ic.commitText("あ", 1); history.recordInsert("あ")
        time += 1301
        ic.commitText("い", 1); history.recordInsert("い")

        assertTrue(history.undo(ic))
        assertEquals("あ", ic.buffer.toString())
        assertTrue(history.undo(ic))
        assertEquals("", ic.buffer.toString())
    }

    @Test
    fun `insert merge breaks around newlines`() {
        val history = EditHistory(clock = { 0L })
        val ic = FakeInputConnection()

        ic.commitText("あ", 1); history.recordInsert("あ")
        ic.commitText("\n", 1); history.recordInsert("\n")
        ic.commitText("い", 1); history.recordInsert("い")

        assertEquals("あ\nい", ic.buffer.toString())
        assertTrue(history.undo(ic)); assertEquals("あ\n", ic.buffer.toString())
        assertTrue(history.undo(ic)); assertEquals("あ", ic.buffer.toString())
        assertTrue(history.undo(ic)); assertEquals("", ic.buffer.toString())
        assertFalse(history.canUndo)
    }

    @Test
    fun `consecutive deletes merge and restore the original text in order on undo`() {
        var time = 0L
        val history = EditHistory(clock = { time })
        val ic = FakeInputConnection()
        ic.commitText("あいう", 1)

        val d1 = ic.getTextBeforeCursor(1, 0).toString()
        ic.deleteSurroundingText(1, 0)
        history.recordDelete(d1)

        time += 50
        val d2 = ic.getTextBeforeCursor(1, 0).toString()
        ic.deleteSurroundingText(1, 0)
        history.recordDelete(d2)

        assertEquals("あ", ic.buffer.toString())
        assertTrue(history.undo(ic))
        assertEquals("あいう", ic.buffer.toString())
        assertFalse(history.canUndo)
    }

    @Test
    fun `a selection delete merges with a following backspace like consecutive deletes`() {
        // 選択範囲の削除も recordDelete を通るため、直後の1文字バックスペースと
        // 同じマージ経路を通る（複数行選択でも文字列としては変わらない）。
        var time = 0L
        val history = EditHistory(clock = { time })
        val ic = FakeInputConnection()
        ic.commitText("abc\nde", 1)

        // 選択範囲 "c\nde" を一括削除したとみなす
        val selected = "c\nde"
        repeat(selected.length) { ic.deleteSurroundingText(1, 0) }
        history.recordDelete(selected)

        time += 50
        val d2 = ic.getTextBeforeCursor(1, 0).toString()
        ic.deleteSurroundingText(1, 0)
        history.recordDelete(d2)

        assertEquals("a", ic.buffer.toString())
        assertTrue(history.undo(ic))
        assertEquals("abc\nde", ic.buffer.toString())
    }

    @Test
    fun `recordModifyLast merges into the previous insert for dakuten conversion`() {
        val history = EditHistory(clock = { 0L })
        val ic = FakeInputConnection()

        ic.commitText("は", 1); history.recordInsert("は")

        ic.deleteSurroundingText(1, 0)
        ic.commitText("ば", 1)
        history.recordModifyLast("は", "ば")

        assertEquals("ば", ic.buffer.toString())
        assertTrue(history.undo(ic))
        assertEquals("", ic.buffer.toString())
        assertFalse(history.canUndo)
    }

    @Test
    fun `recordModifyLast falls back to a standalone replace when it cannot merge`() {
        val history = EditHistory(clock = { 0L })
        val ic = FakeInputConnection()
        ic.commitText("XY", 1)

        ic.deleteSurroundingText(1, 0)
        ic.commitText("Z", 1)
        history.recordModifyLast("Y", "Z")

        assertEquals("XZ", ic.buffer.toString())
        assertTrue(history.undo(ic))
        assertEquals("XY", ic.buffer.toString())
    }

    @Test
    fun `undo discards all history when the text was changed externally`() {
        val history = EditHistory(clock = { 0L })
        val ic = FakeInputConnection()

        ic.commitText("あ", 1); history.recordInsert("あ")
        ic.commitText("外部", 1) // 他アプリ/物理キーボードなど、この IME を介さない変更

        assertFalse(history.undo(ic))
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun `redo restores an undone insert`() {
        val history = EditHistory(clock = { 0L })
        val ic = FakeInputConnection()

        ic.commitText("あ", 1); history.recordInsert("あ")
        assertTrue(history.undo(ic))
        assertEquals("", ic.buffer.toString())
        assertTrue(history.canRedo)

        assertTrue(history.redo(ic))
        assertEquals("あ", ic.buffer.toString())
        assertFalse(history.canRedo)
        assertTrue(history.canUndo)
    }

    @Test
    fun `empty text does not create history entries`() {
        val history = EditHistory(clock = { 0L })
        history.recordInsert("")
        history.recordDelete("")
        assertFalse(history.canUndo)
    }

    @Test
    fun `undo stack is capped at maxSize`() {
        val history = EditHistory(maxSize = 2, clock = { 0L })
        val ic = FakeInputConnection()

        ic.commitText("a", 1); history.recordInsert("a")
        history.breakMerge()
        ic.commitText("b", 1); history.recordInsert("b")
        history.breakMerge()
        ic.commitText("c", 1); history.recordInsert("c")

        assertTrue(history.undo(ic))
        assertTrue(history.undo(ic))
        assertFalse(history.undo(ic))
        assertEquals("a", ic.buffer.toString())
    }
}
