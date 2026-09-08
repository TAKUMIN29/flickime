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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

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
         * @param alternates フリック判定が際どかった場合の次点候補（確率降順）。
         *   かな漢字変換エンジンに誤フリックを許容してもらうためのヒントとして使う。
         */
        fun onKey(key: KeySpec, flick: Flick, tapCount: Int, alternates: List<FlickCandidate> = emptyList())

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

        /** 次点候補として渡す確率の下限（これ未満は無視する）。 */
        private const val MIN_ALTERNATE_PROBABILITY = 0.08f

        /** 次点候補として渡す最大件数。 */
        private const val MAX_ALTERNATES = 2

        /** 指を置いた位置がキーの境界からこの割合(0〜0.5)以内なら、隣のキーだった可能性を考える。 */
        private const val KEY_BOUNDARY_MARGIN_FRACTION = 0.22f

        /** 隣のキーだった可能性は、同じキー内のフリック違いより確信度を割り引いて扱う。 */
        private const val NEIGHBOR_KEY_WEIGHT = 0.6f
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
    // 押したキーの中で、指を置いた位置がどこだったか(0〜1)。境界付近かどうかの判定に使う。
    private var downFracX = 0.5f
    private var downFracY = 0.5f
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
                onUp(event.x, event.y)
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
        val colW = gridW / cols
        val col = (((x - offsetX) / colW).toInt()).coerceIn(0, cols - 1)
        val rowH = height.toFloat() / rows

        stopRepeat()
        activeRow = row
        activeCol = col
        downX = x
        downY = y
        downFracX = ((x - offsetX - col * colW) / colW).coerceIn(0f, 1f)
        downFracY = ((y - row * rowH) / rowH).coerceIn(0f, 1f)
        currentFlick = Flick.CENTER
        repeatFired = false

        val now = SystemClock.uptimeMillis()
        tapCount = if (row == lastRow && col == lastCol && now - lastUpAt <= TAP_CYCLE_MS) tapCount + 1 else 0

        val spec = keyRows[row][col]
        if (hapticEnabled) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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

    private fun onUp(x: Float, y: Float) {
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
        val spec = keyRows[row][col]
        val alternates = if (spec.type == KeyType.CHAR) {
            (flickAlternates(spec, flick, x - downX, y - downY) + keyAlternates(row, col, flick))
                .sortedByDescending { it.probability }
                .distinctBy { it.text }
                .take(MAX_ALTERNATES)
        } else {
            emptyList()
        }
        listener?.onKey(spec, flick, count, alternates)
    }

    /**
     * 指を離した位置から、本命([chosen])以外の方向にも「際どく」該当していた
     * 可能性を確率として見積もり、次点候補の文字リストにする。
     */
    private fun flickAlternates(spec: KeySpec, chosen: Flick, dx: Float, dy: Float): List<FlickCandidate> {
        return scoreFlicks(dx, dy)
            .asSequence()
            .filter { (candidateFlick, probability) ->
                candidateFlick != chosen && probability >= MIN_ALTERNATE_PROBABILITY
            }
            .mapNotNull { (candidateFlick, probability) ->
                spec.output(candidateFlick)?.let { text -> FlickCandidate(text, probability) }
            }
            .take(MAX_ALTERNATES)
            .toList()
    }

    /**
     * 指を置いた位置が隣のキーとの境界に近かった場合、そもそも押そうとしていたのは
     * 隣のキーだったかもしれないと見積もり、次点候補にする。
     * フリック方向は本命キーで判定したものをそのまま隣のキーにも当てはめる
     * （同じ向きに指を動かしたはずなので）。
     */
    private fun keyAlternates(row: Int, col: Int, chosen: Flick): List<FlickCandidate> {
        val neighbors = mutableListOf<Pair<KeySpec, Float>>()
        val cols = colCount(row)
        if (downFracX < KEY_BOUNDARY_MARGIN_FRACTION && col > 0) {
            val proximity = (KEY_BOUNDARY_MARGIN_FRACTION - downFracX) / KEY_BOUNDARY_MARGIN_FRACTION
            neighbors += keyRows[row][col - 1] to proximity
        }
        if (downFracX > 1f - KEY_BOUNDARY_MARGIN_FRACTION && col < cols - 1) {
            val proximity = (downFracX - (1f - KEY_BOUNDARY_MARGIN_FRACTION)) / KEY_BOUNDARY_MARGIN_FRACTION
            neighbors += keyRows[row][col + 1] to proximity
        }
        val rows = keyRows.size
        if (downFracY < KEY_BOUNDARY_MARGIN_FRACTION && row > 0 && col < colCount(row - 1)) {
            val proximity = (KEY_BOUNDARY_MARGIN_FRACTION - downFracY) / KEY_BOUNDARY_MARGIN_FRACTION
            neighbors += keyRows[row - 1][col] to proximity
        }
        if (downFracY > 1f - KEY_BOUNDARY_MARGIN_FRACTION && row < rows - 1 && col < colCount(row + 1)) {
            val proximity = (downFracY - (1f - KEY_BOUNDARY_MARGIN_FRACTION)) / KEY_BOUNDARY_MARGIN_FRACTION
            neighbors += keyRows[row + 1][col] to proximity
        }

        return neighbors
            .asSequence()
            .filter { (spec, _) -> spec.type == KeyType.CHAR }
            .mapNotNull { (spec, proximity) ->
                val text = spec.output(chosen) ?: spec.output(Flick.CENTER)
                text?.let { FlickCandidate(it, proximity * NEIGHBOR_KEY_WEIGHT) }
            }
            .filter { it.probability >= MIN_ALTERNATE_PROBABILITY }
            .toList()
    }

    /**
     * 5方向それぞれについて「これだった可能性」をスコア化する（確率の合計は1）。
     * CENTER は移動距離がしきい値に近いほど、方向キーは実際の角度が近いほど高くなる。
     */
    private fun scoreFlicks(dx: Float, dy: Float): List<Pair<Flick, Float>> {
        val r = hypot(dx, dy)
        val scores = LinkedHashMap<Flick, Float>()
        scores[Flick.CENTER] = (1f - r / flickThresholdPx).coerceIn(0f, 1f)

        if (r > 1f) {
            val theta = atan2(dy.toDouble(), dx.toDouble())
            // しきい値をわずかに超えた程度では、方向キーの確信度もまだ低くしておく。
            val magnitude = (r / (flickThresholdPx * 1.5f)).toDouble().coerceIn(0.0, 1.0)
            val directionAngles = listOf(
                Flick.RIGHT to 0.0,
                Flick.DOWN to PI / 2,
                Flick.LEFT to PI,
                Flick.UP to -PI / 2,
            )
            for ((flick, angle) in directionAngles) {
                var diff = theta - angle
                while (diff > PI) diff -= 2 * PI
                while (diff < -PI) diff += 2 * PI
                val alignment = cos(diff).coerceAtLeast(0.0)
                scores[flick] = (alignment * magnitude).toFloat()
            }
        } else {
            for (flick in listOf(Flick.LEFT, Flick.UP, Flick.RIGHT, Flick.DOWN)) scores[flick] = 0f
        }

        val total = scores.values.sum().takeIf { it > 0f } ?: 1f
        return scores.entries.map { it.key to (it.value / total) }.sortedByDescending { it.second }
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
