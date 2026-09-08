package com.example.flickime.keyboard

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/**
 * 「本当は別のキー/別の方向を狙っていたのでは？」という次点候補を見積もる。
 *
 * フリック入力の打ち間違いには2種類ある。
 *  1. 同じキーの中でフリック方向が際どかった（例: な の右と下の中間くらいで指を離した）
 *  2. そもそも隣のキーを押してしまった（例: た の下「と」を狙って、な の下「の」を押した）
 *
 * どちらも確率つきの候補として出し、かな漢字変換の校正候補を作る材料にする。
 * View から切り離した純粋な計算なので、そのまま単体テストできる。
 */
object FlickAlternates {

    /** 同じキー内のフリック方向違いを次点候補として採用する確率の下限。 */
    private const val MIN_ALTERNATE_PROBABILITY = 0.08f

    /** 同じキー内のフリック方向違いから採る最大件数。 */
    private const val MAX_FLICK_ALTERNATES = 2

    /** 隣のキーから採る最大件数。 */
    private const val MAX_NEIGHBOR_ALTERNATES = 2

    /** 最終的に返す次点候補の最大件数。 */
    private const val MAX_TOTAL_ALTERNATES = 4

    /** 指を置いた位置がキーの境界からこの割合(0〜0.5)以内なら「境界に近い」とみなす。 */
    private const val KEY_BOUNDARY_MARGIN_FRACTION = 0.22f

    /**
     * 隣のキーを押し間違えた可能性の下限。
     * キーの中央を押していても配列上の隣接キーは常に候補として拾えるようにする
     * （狙いを外したというより、単に隣のキーだと思い込んでいる打ち間違いに対応するため）。
     */
    private const val BASE_NEIGHBOR_PROBABILITY = 0.12f

    /** 境界に近いほど上乗せする確率。 */
    private const val BOUNDARY_PROXIMITY_BONUS = 0.5f

    /** 12キー配列は列が狭いので、左右の押し間違いを上下より起こりやすいとみなす。 */
    private const val HORIZONTAL_NEIGHBOR_WEIGHT = 1.0f
    private const val VERTICAL_NEIGHBOR_WEIGHT = 0.7f

    /**
     * @param row,col 実際に指を置いたキーの位置。
     * @param chosen 実際に採用されたフリック方向。
     * @param dx,dy 指を置いてから離すまでの移動量(px)。
     * @param fracX,fracY 押したキーの中で指を置いた位置(0〜1)。
     * @param flickThresholdPx フリックと判定する移動距離(px)。
     */
    fun compute(
        keyRows: List<List<KeySpec>>,
        row: Int,
        col: Int,
        chosen: Flick,
        dx: Float,
        dy: Float,
        fracX: Float,
        fracY: Float,
        flickThresholdPx: Float,
    ): List<FlickCandidate> {
        val spec = keyRows.getOrNull(row)?.getOrNull(col) ?: return emptyList()
        val chosenText = spec.output(chosen)
        return (
            flickAlternates(spec, chosen, dx, dy, flickThresholdPx) +
                neighborAlternates(keyRows, row, col, chosen, fracX, fracY)
            )
            .sortedByDescending { it.probability }
            .distinctBy { it.text }
            .filter { it.text != chosenText }
            .take(MAX_TOTAL_ALTERNATES)
    }

    /** 同じキーの中で、本命([chosen])以外の方向にも際どく該当していた可能性。 */
    private fun flickAlternates(
        spec: KeySpec,
        chosen: Flick,
        dx: Float,
        dy: Float,
        flickThresholdPx: Float,
    ): List<FlickCandidate> = scoreFlicks(dx, dy, flickThresholdPx)
        .asSequence()
        .filter { (flick, probability) -> flick != chosen && probability >= MIN_ALTERNATE_PROBABILITY }
        .mapNotNull { (flick, probability) ->
            spec.output(flick)?.let { FlickCandidate(it, probability) }
        }
        .take(MAX_FLICK_ALTERNATES)
        .toList()

    /**
     * 隣のキーを押すつもりだった可能性。
     * フリック方向は本命キーで判定したものをそのまま隣のキーにも当てはめる
     * （指は同じ向きに動かしたはずなので）。
     */
    private fun neighborAlternates(
        keyRows: List<List<KeySpec>>,
        row: Int,
        col: Int,
        chosen: Flick,
        fracX: Float,
        fracY: Float,
    ): List<FlickCandidate> {
        val cols = keyRows[row].size
        val neighbors = mutableListOf<Pair<KeySpec, Float>>()

        if (col > 0) {
            neighbors += keyRows[row][col - 1] to
                neighborProbability(edgeProximity(fracX), HORIZONTAL_NEIGHBOR_WEIGHT)
        }
        if (col < cols - 1) {
            neighbors += keyRows[row][col + 1] to
                neighborProbability(edgeProximity(1f - fracX), HORIZONTAL_NEIGHBOR_WEIGHT)
        }
        keyRows.getOrNull(row - 1)?.getOrNull(col)?.let {
            neighbors += it to neighborProbability(edgeProximity(fracY), VERTICAL_NEIGHBOR_WEIGHT)
        }
        keyRows.getOrNull(row + 1)?.getOrNull(col)?.let {
            neighbors += it to neighborProbability(edgeProximity(1f - fracY), VERTICAL_NEIGHBOR_WEIGHT)
        }

        return neighbors
            .asSequence()
            .filter { (spec, _) -> spec.type == KeyType.CHAR }
            .mapNotNull { (spec, probability) ->
                val text = spec.output(chosen) ?: spec.output(Flick.CENTER)
                text?.let { FlickCandidate(it, probability) }
            }
            .sortedByDescending { it.probability }
            .take(MAX_NEIGHBOR_ALTERNATES)
            .toList()
    }

    /** キー内の位置([fracToEdge] は 0 でその辺に接している)から、境界への近さ(0〜1)を出す。 */
    private fun edgeProximity(fracToEdge: Float): Float =
        ((KEY_BOUNDARY_MARGIN_FRACTION - fracToEdge) / KEY_BOUNDARY_MARGIN_FRACTION).coerceIn(0f, 1f)

    private fun neighborProbability(proximity: Float, axisWeight: Float): Float =
        (BASE_NEIGHBOR_PROBABILITY + proximity * BOUNDARY_PROXIMITY_BONUS) * axisWeight

    /**
     * 5方向それぞれについて「これだった可能性」をスコア化する（確率の合計は1）。
     * CENTER は移動距離がしきい値に近いほど、方向キーは実際の角度が近いほど高くなる。
     */
    private fun scoreFlicks(dx: Float, dy: Float, flickThresholdPx: Float): List<Pair<Flick, Float>> {
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
}
