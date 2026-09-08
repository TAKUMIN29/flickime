package com.example.flickime.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.flickime.R
import kotlin.math.abs

/**
 * ケータイ配列のフリックキーボード。
 *
 * 指を置いたキーは離すまで固定され、そこからの移動方向でフリックを決める
 * （実機のフリック入力と同じ挙動）。
 */
class FlickKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    interface Listener {
        /**
         * キー確定。
         * @param tapCount 同じキーを続けてタップした回数（0 始まり）。文字送りに使う。
         */
        fun onKey(key: KeySpec, flick: Flick, tapCount: Int)

        /** 長押し中のリピート（削除キーなど）。 */
        fun onKeyRepeat(key: KeySpec)
    }

    /** 片手モード。キー配列全体を画面の片側へ寄せて幅を縮める。 */
    enum class OneHandedMode { OFF, LEFT, RIGHT }

    companion object {
        /** 連続タップを「文字送り」とみなす間隔。 */
        private const val TAP_CYCLE_MS = 900L
        private const val REPEAT_START_DELAY_MS = 400L
        private const val REPEAT_INTERVAL_MS = 55L

        /** 片手モード時のキー配列全体の幅（画面幅に対する比率）。 */
        private const val ONE_HANDED_WIDTH_FRACTION = 0.78f
    }

    var listener: Listener? = null
    var hapticEnabled: Boolean = true

    var oneHandedMode: OneHandedMode = OneHandedMode.OFF
        set(value) {
            field = value
            cancelTouch()
            invalidate()
        }

    /** フリックと判定する移動距離(px)。 */
    var flickThresholdPx: Float = dp(22f)

    /** フリック候補を描画するオーバーレイ。 */
    var guideView: FlickGuideView? = null

    var keyRows: List<List<KeySpec>> = KeyLayouts.KANA
        set(value) {
            field = value
            cancelTouch()
            invalidate()
        }

    var rowHeightPx: Float = dp(52f)
        set(value) {
            field = value
            requestLayout()
        }

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val colorKey = ContextCompat.getColor(context, R.color.ime_key_bg)
    private val colorFunc = ContextCompat.getColor(context, R.color.ime_key_func_bg)
    private val colorPressed = ContextCompat.getColor(context, R.color.ime_key_pressed)
    private val colorText = ContextCompat.getColor(context, R.color.ime_text)
    private val colorTextSub = ContextCompat.getColor(context, R.color.ime_text_sub)

    private val gap = dp(2.5f)
    private val corner = dp(7f)
    private val charTextSize = dp(20f)
    private val funcTextSize = dp(12f)

    private val rect = RectF()

    // --- タッチ状態 ---
    private var activeRow = -1
    private var activeCol = -1
    private var downX = 0f
    private var downY = 0f
    private var currentFlick = Flick.CENTER
    private var lastRow = -1
    private var lastCol = -1
    private var lastUpAt = 0L
    private var tapCount = 0
    private var repeatFired = false

    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    private val locSelf = IntArray(2)
    private val locGuide = IntArray(2)

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (rowHeightPx * keyRows.size).toInt()
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec))
    }

    private fun colCount(row: Int): Int = keyRows.getOrNull(row)?.size ?: 0

    /** 片手モード時のキー配列全体の幅(px)。 */
    private fun gridWidth(): Float =
        if (oneHandedMode == OneHandedMode.OFF) width.toFloat() else width * ONE_HANDED_WIDTH_FRACTION

    /** 片手モード時のキー配列の左端オフセット(px)。 */
    private fun gridOffsetX(): Float =
        if (oneHandedMode == OneHandedMode.RIGHT) width - gridWidth() else 0f

    private fun fillKeyRect(row: Int, col: Int, out: RectF) {
        val cols = colCount(row).coerceAtLeast(1)
        val offsetX = gridOffsetX()
        val kw = gridWidth() / cols
        val kh = height.toFloat() / keyRows.size.coerceAtLeast(1)
        out.set(offsetX + col * kw, row * kh, offsetX + (col + 1) * kw, (row + 1) * kh)
    }

    override fun onDraw(canvas: Canvas) {
        if (keyRows.isEmpty() || width == 0 || height == 0) return

        for (row in keyRows.indices) {
            val keys = keyRows[row]
            for (col in keys.indices) {
                val spec = keys[col]
                fillKeyRect(row, col, rect)
                rect.inset(gap, gap)

                val pressed = row == activeRow && col == activeCol
                keyPaint.color = when {
                    pressed -> colorPressed
                    spec.isFunction -> colorFunc
                    else -> colorKey
                }
                canvas.drawRoundRect(rect, corner, corner, keyPaint)

                val isChar = spec.type == KeyType.CHAR || spec.type == KeyType.MODIFIER
                textPaint.color = if (spec.isFunction && !isChar) colorTextSub else colorText
                // ABC や 小゛゜ のような複数文字ラベルは縮めてキー内に収める
                textPaint.textSize = when {
                    !isChar -> funcTextSize
                    spec.label.length >= 3 -> charTextSize * 0.6f
                    spec.label.length == 2 -> charTextSize * 0.75f
                    else -> charTextSize
                }
                val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(spec.label, rect.centerX(), baseline, textPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event.x, event.y)
            MotionEvent.ACTION_MOVE -> onMove(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                onUp()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> cancelTouch()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun onDown(x: Float, y: Float) {
        val rows = keyRows.size
        if (rows == 0 || width == 0 || height == 0) return

        // 片手モードでキー配列の外側（余白）に触れた場合は何もしない
        val offsetX = gridOffsetX()
        val gridW = gridWidth()
        if (x < offsetX || x >= offsetX + gridW) return

        val row = ((y / (height.toFloat() / rows)).toInt()).coerceIn(0, rows - 1)
        val cols = colCount(row)
        if (cols == 0) return
        val col = (((x - offsetX) / (gridW / cols)).toInt()).coerceIn(0, cols - 1)

        stopRepeat()
        activeRow = row
        activeCol = col
        downX = x
        downY = y
        currentFlick = Flick.CENTER
        repeatFired = false

        val now = SystemClock.uptimeMillis()
        tapCount = if (row == lastRow && col == lastCol && now - lastUpAt <= TAP_CYCLE_MS) tapCount + 1 else 0

        val spec = keyRows[row][col]
        if (hapticEnabled) {
            performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
        }
        if (spec.hasFlickVariants()) showGuide(row, col, spec)
        if (spec.type == KeyType.BACKSPACE) startRepeat(spec)
        invalidate()
    }

    private fun onMove(x: Float, y: Float) {
        if (activeRow < 0) return
        val spec = keyRows[activeRow][activeCol]

        val dx = x - downX
        val dy = y - downY
        val flick = if (abs(dx) < flickThresholdPx && abs(dy) < flickThresholdPx) {
            Flick.CENTER
        } else if (abs(dx) >= abs(dy)) {
            if (dx < 0) Flick.LEFT else Flick.RIGHT
        } else {
            if (dy < 0) Flick.UP else Flick.DOWN
        }

        // 割り当てのない方向へフリックしても中央扱いにする
        val resolved = if (flick == Flick.CENTER || spec.output(flick) != null) flick else Flick.CENTER
        if (resolved != currentFlick) {
            currentFlick = resolved
            if (resolved != Flick.CENTER) stopRepeat()
            guideView?.updateSelection(resolved)
        }
    }

    private fun onUp() {
        val row = activeRow
        val col = activeCol
        val flick = currentFlick
        val count = tapCount
        val fired = repeatFired

        stopRepeat()
        guideView?.hide()
        activeRow = -1
        activeCol = -1
        invalidate()

        if (row < 0 || col < 0) return
        lastRow = row
        lastCol = col
        lastUpAt = SystemClock.uptimeMillis()
        // フリックした場合は文字送りの連鎖を切る
        if (flick != Flick.CENTER) tapCount = -1

        if (fired) return
        listener?.onKey(keyRows[row][col], flick, count)
    }

    private fun cancelTouch() {
        stopRepeat()
        guideView?.hide()
        activeRow = -1
        activeCol = -1
        invalidate()
    }

    private fun showGuide(row: Int, col: Int, spec: KeySpec) {
        val guide = guideView ?: return
        getLocationInWindow(locSelf)
        guide.getLocationInWindow(locGuide)
        val offsetX = (locSelf[0] - locGuide[0]).toFloat()
        val offsetY = (locSelf[1] - locGuide[1]).toFloat()

        fillKeyRect(row, col, rect)
        guide.show(
            rect.centerX() + offsetX,
            rect.centerY() + offsetY,
            rect.width(),
            rect.height(),
            spec.chars,
            Flick.CENTER,
        )
    }

    private fun startRepeat(spec: KeySpec) {
        val runnable = object : Runnable {
            override fun run() {
                repeatFired = true
                listener?.onKeyRepeat(spec)
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
        repeatRunnable = runnable
        handler.postDelayed(runnable, REPEAT_START_DELAY_MS)
    }

    private fun stopRepeat() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
    }

    override fun onDetachedFromWindow() {
        stopRepeat()
        super.onDetachedFromWindow()
    }
}
