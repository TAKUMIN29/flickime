package com.example.flickime.edit

import android.os.SystemClock
import android.view.inputmethod.InputConnection

/**
 * IME 自身が行った編集の履歴。
 *
 * Android には OS 全体の undo/redo は無いため、
 * 「この IME が InputConnection 経由で行った変更」だけを記録して打ち消す。
 * 他アプリ側やハードウェアキーボードで直接変更された場合は
 * undo 実行時の照合に失敗するので、その時点で履歴を破棄する。
 */
sealed class EditOp {
    /** text を挿入した。 */
    data class Insert(var text: String) : EditOp()

    /** text を（カーソル手前から）削除した。 */
    data class Delete(var text: String) : EditOp()

    /** before を after に置き換えた（濁点付与など）。 */
    data class Replace(val before: String, val after: String) : EditOp()
}

class EditHistory(
    private val maxSize: Int = 200,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) {

    companion object {
        /** この時間内の連続入力は 1 回の undo でまとめて戻す。 */
        private const val MERGE_WINDOW_MS = 1300L
    }

    private val undoStack = ArrayDeque<EditOp>()
    private val redoStack = ArrayDeque<EditOp>()

    private var lastRecordedAt = 0L
    private var mergeBlocked = true

    /** ボタンの活性状態を更新するためのコールバック。 */
    var onChanged: (() -> Unit)? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        mergeBlocked = true
        onChanged?.invoke()
    }

    /** カーソル移動など、まとめてはいけない区切りが入ったことを通知する。 */
    fun breakMerge() {
        mergeBlocked = true
    }

    fun recordInsert(text: String) {
        if (text.isEmpty()) return
        redoStack.clear()
        val now = clock()
        val last = undoStack.lastOrNull()
        val mergeable = !mergeBlocked &&
            last is EditOp.Insert &&
            now - lastRecordedAt <= MERGE_WINDOW_MS &&
            !last.text.endsWith("\n") &&
            text != "\n"
        if (mergeable && last is EditOp.Insert) {
            last.text += text
        } else {
            push(EditOp.Insert(text))
        }
        mergeBlocked = false
        lastRecordedAt = now
        onChanged?.invoke()
    }

    fun recordDelete(text: String) {
        if (text.isEmpty()) return
        redoStack.clear()
        val now = clock()
        val last = undoStack.lastOrNull()
        val mergeable = !mergeBlocked &&
            last is EditOp.Delete &&
            now - lastRecordedAt <= MERGE_WINDOW_MS
        if (mergeable && last is EditOp.Delete) {
            // 後ろから消していくので、先に消した分より前に付ける
            last.text = text + last.text
        } else {
            push(EditOp.Delete(text))
        }
        mergeBlocked = false
        lastRecordedAt = now
        onChanged?.invoke()
    }

    /**
     * 直前に入力した文字を差し替えたときに使う（文字送り・濁点付与）。
     * 直前の Insert の末尾が [oldText] ならその中身を書き換えて 1 操作にまとめる。
     */
    fun recordModifyLast(oldText: String, newText: String) {
        val last = undoStack.lastOrNull()
        if (last is EditOp.Insert && last.text.endsWith(oldText)) {
            last.text = last.text.dropLast(oldText.length) + newText
            redoStack.clear()
            lastRecordedAt = clock()
            onChanged?.invoke()
            return
        }
        recordReplace(oldText, newText)
    }

    fun recordReplace(before: String, after: String) {
        if (before == after) return
        redoStack.clear()
        push(EditOp.Replace(before, after))
        mergeBlocked = true
        lastRecordedAt = clock()
        onChanged?.invoke()
    }

    private fun push(op: EditOp) {
        undoStack.addLast(op)
        while (undoStack.size > maxSize) undoStack.removeFirst()
    }

    /** @return 実際に取り消せたら true */
    fun undo(ic: InputConnection): Boolean {
        val op = undoStack.removeLastOrNull() ?: return false
        val ok = when (op) {
            is EditOp.Insert -> deleteBefore(ic, op.text)
            is EditOp.Delete -> {
                ic.commitText(op.text, 1)
                true
            }
            is EditOp.Replace -> replaceBefore(ic, from = op.after, to = op.before)
        }
        if (ok) {
            redoStack.addLast(op)
            mergeBlocked = true
        } else {
            // 想定と実際のテキストがずれている＝外部で編集された。安全のため履歴を捨てる。
            undoStack.clear()
            redoStack.clear()
        }
        onChanged?.invoke()
        return ok
    }

    /** @return 実際にやり直せたら true */
    fun redo(ic: InputConnection): Boolean {
        val op = redoStack.removeLastOrNull() ?: return false
        val ok = when (op) {
            is EditOp.Insert -> {
                ic.commitText(op.text, 1)
                true
            }
            is EditOp.Delete -> deleteBefore(ic, op.text)
            is EditOp.Replace -> replaceBefore(ic, from = op.before, to = op.after)
        }
        if (ok) {
            undoStack.addLast(op)
            mergeBlocked = true
        } else {
            undoStack.clear()
            redoStack.clear()
        }
        onChanged?.invoke()
        return ok
    }

    /** カーソル直前が [text] と一致していれば削除する。 */
    private fun deleteBefore(ic: InputConnection, text: String): Boolean {
        val actual = ic.getTextBeforeCursor(text.length, 0)?.toString() ?: return false
        if (actual != text) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(text.length, 0)
        ic.endBatchEdit()
        return true
    }

    private fun replaceBefore(ic: InputConnection, from: String, to: String): Boolean {
        val actual = ic.getTextBeforeCursor(from.length, 0)?.toString() ?: return false
        if (actual != from) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(from.length, 0)
        ic.commitText(to, 1)
        ic.endBatchEdit()
        return true
    }
}
