package com.example.flickime.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.flickime.R

/**
 * フリック中に表示する候補の吹き出し。
 *
 * 入力ビュー全体に重ねて配置し、最上段のキーでも上方向の候補が
 * ツールバーの上まで描けるようにしている。
 */
class FlickGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var showing = false
    private var centerX = 0f
    private var centerY = 0f
    private var cellW = 0f
    private var cellH = 0f
    private var labels: List<String> = emptyList()
    private var selected: Flick = Flick.CENTER

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ime_guide_bg)
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ime_guide_selected)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.ime_divider)
        strokeWidth = resources.displayMetrics.density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.ime_text)
    }
    private val selectedTextColor = ContextCompat.getColor(context, R.color.ime_guide_text_selected)
    private val normalTextColor = ContextCompat.getColor(context, R.color.ime_text)

    private val rect = RectF()
    private val density = resources.displayMetrics.density

    init {
        // タッチはこのビューを素通りさせ、下のキーボードに届ける
        isClickable = false
        isFocusable = false
    }

    /**
     * 吹き出しを表示する。座標はこのビューのローカル座標系。
     *
     * @param labels [Flick.index] 順のラベル（空文字は非表示）
     */
    fun show(cx: Float, cy: Float, keyW: Float, keyH: Float, labels: List<String>, selected: Flick) {
        this.centerX = cx
        this.centerY = cy
        this.cellW = keyW
        this.cellH = keyH
        this.labels = labels
        this.selected = selected
        this.showing = true
        invalidate()
    }

    fun updateSelection(selected: Flick) {
        if (this.selected == selected) return
        this.selected = selected
        invalidate()
    }

    fun hide() {
        if (!showing) return
        showing = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (!showing || labels.isEmpty()) return

        val w = cellW * 0.94f
        val h = cellH * 0.94f
        val radius = 8f * density

        // 5個の吹き出し全体がビュー内に収まるよう、はみ出す分だけ十字ごとずらす
        var shiftX = 0f
        var shiftY = 0f
        val leftEdge = centerX - cellW - w / 2f
        val rightEdge = centerX + cellW + w / 2f
        val topEdge = centerY - cellH - h / 2f
        val bottomEdge = centerY + cellH + h / 2f
        if (leftEdge < 0f) shiftX = -leftEdge
        if (rightEdge > width) shiftX = width - rightEdge
        if (topEdge < 0f) shiftY = -topEdge
        if (bottomEdge > height) shiftY = height - bottomEdge

        textPaint.textSize = minOf(w, h) * 0.44f

        for (flick in Flick.values()) {
            val label = labels.getOrNull(flick.index).orEmpty()
            if (label.isEmpty()) continue

            val bx = centerX + shiftX + when (flick) {
                Flick.LEFT -> -cellW
                Flick.RIGHT -> cellW
                else -> 0f
            }
            val by = centerY + shiftY + when (flick) {
                Flick.UP -> -cellH
                Flick.DOWN -> cellH
                else -> 0f
            }

            rect.set(bx - w / 2f, by - h / 2f, bx + w / 2f, by + h / 2f)
            val isSelected = flick == selected
            canvas.drawRoundRect(rect, radius, radius, if (isSelected) selectedPaint else bgPaint)
            if (!isSelected) canvas.drawRoundRect(rect, radius, radius, borderPaint)

            textPaint.color = if (isSelected) selectedTextColor else normalTextColor
            val baseline = by - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(label, bx, baseline, textPaint)
        }
    }
}
