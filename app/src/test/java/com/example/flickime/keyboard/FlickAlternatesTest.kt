package com.example.flickime.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlickAlternatesTest {

    /** かな配列での「な」キーの位置（2行目の中央列）。 */
    private val naRow = 1
    private val naCol = 2

    /** 下フリックの移動量。しきい値を十分に超える素直なフリック。 */
    private val downDx = 0f
    private val downDy = 60f
    private val threshold = 22f

    private fun computeForNaDown(fracX: Float, fracY: Float): List<FlickCandidate> =
        FlickAlternates.compute(
            keyRows = KeyLayouts.KANA,
            row = naRow,
            col = naCol,
            chosen = Flick.DOWN,
            dx = downDx,
            dy = downDy,
            fracX = fracX,
            fracY = fracY,
            flickThresholdPx = threshold,
        )

    @Test
    fun `な キーが「の」を出すこと（前提の確認）`() {
        assertEquals("の", KeyLayouts.KANA[naRow][naCol].output(Flick.DOWN))
        assertEquals("と", KeyLayouts.KANA[naRow][naCol - 1].output(Flick.DOWN))
    }

    @Test
    fun `な の中央を押して下フリックしても、隣の た の「と」が候補に挙がる`() {
        // 「てすとした」を「てすのした」と打ち間違えるケース。キーの中央を素直に押していても
        // 配列上の隣が候補に出ないと校正候補を作れない。
        val alternates = computeForNaDown(fracX = 0.5f, fracY = 0.5f)
        assertTrue("と がない: $alternates", alternates.any { it.text == "と" })
    }

    @Test
    fun `た 寄りを押していたときは「と」の確率がより高くなる`() {
        val centered = computeForNaDown(fracX = 0.5f, fracY = 0.5f)
        val leaningLeft = computeForNaDown(fracX = 0.02f, fracY = 0.5f)

        val centeredTo = centered.first { it.text == "と" }.probability
        val leaningTo = leaningLeft.first { it.text == "と" }.probability
        assertTrue("$leaningTo は $centeredTo より大きいはず", leaningTo > centeredTo)
    }

    @Test
    fun `本命の文字そのものは候補に含めない`() {
        val alternates = computeForNaDown(fracX = 0.5f, fracY = 0.5f)
        assertTrue("の が混ざっている: $alternates", alternates.none { it.text == "の" })
    }

    @Test
    fun `候補は確率の降順で重複なく返る`() {
        val alternates = computeForNaDown(fracX = 0.1f, fracY = 0.9f)
        assertEquals(alternates.map { it.text }.distinct(), alternates.map { it.text })
        assertEquals(alternates.sortedByDescending { it.probability }, alternates)
    }

    @Test
    fun `機能キーは候補にしない`() {
        // 1行目左端は MODE キー。その隣（あ）を押したとき、機能キーが候補に混ざらないこと。
        val alternates = FlickAlternates.compute(
            keyRows = KeyLayouts.KANA,
            row = 0,
            col = 1,
            chosen = Flick.CENTER,
            dx = 0f,
            dy = 0f,
            fracX = 0.0f,
            fracY = 0.5f,
            flickThresholdPx = threshold,
        )
        val functionLabels = KeyLayouts.KANA.flatten().filter { it.isFunction }.map { it.label }
        assertTrue(alternates.none { it.text in functionLabels })
    }

    @Test
    fun `際どい斜めフリックでは同じキーの別方向も候補になる`() {
        // 右下 45 度。DOWN が採用されたとして、RIGHT(ね) も次点に挙がってほしい。
        val alternates = FlickAlternates.compute(
            keyRows = KeyLayouts.KANA,
            row = naRow,
            col = naCol,
            chosen = Flick.DOWN,
            dx = 40f,
            dy = 40f,
            fracX = 0.5f,
            fracY = 0.5f,
            flickThresholdPx = threshold,
        )
        assertTrue("ね がない: $alternates", alternates.any { it.text == "ね" })
    }
}
