package com.example.flickime.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KanaModifierTest {

    @Test
    fun `cycle steps through seion to dakuon to handakuon and wraps`() {
        assertEquals('ば', KanaModifier.cycle('は'))
        assertEquals('ぱ', KanaModifier.cycle('ば'))
        assertEquals('は', KanaModifier.cycle('ぱ'))
    }

    @Test
    fun `cycle handles small kana chain`() {
        assertEquals('っ', KanaModifier.cycle('つ'))
        assertEquals('づ', KanaModifier.cycle('っ'))
        assertEquals('つ', KanaModifier.cycle('づ'))
    }

    @Test
    fun `cycle wraps two character chain`() {
        assertEquals('ぁ', KanaModifier.cycle('あ'))
        assertEquals('あ', KanaModifier.cycle('ぁ'))
    }

    @Test
    fun `cycle returns null for non-modifiable characters`() {
        assertNull(KanaModifier.cycle('ん'))
        assertNull(KanaModifier.cycle('a'))
    }

    @Test
    fun `dakuten toggles both directions`() {
        assertEquals('が', KanaModifier.dakuten('か'))
        assertEquals('か', KanaModifier.dakuten('が'))
        assertNull(KanaModifier.dakuten('あ'))
    }

    @Test
    fun `handakuten toggles only ha row`() {
        assertEquals('ぱ', KanaModifier.handakuten('は'))
        assertEquals('は', KanaModifier.handakuten('ぱ'))
        assertNull(KanaModifier.handakuten('か'))
    }

    @Test
    fun `small toggles both directions`() {
        assertEquals('ぁ', KanaModifier.small('あ'))
        assertEquals('あ', KanaModifier.small('ぁ'))
        assertNull(KanaModifier.small('か'))
    }

    @Test
    fun `isModifiable reflects all maps`() {
        assertTrue(KanaModifier.isModifiable('は')) // cycle + dakuten + handakuten
        assertTrue(KanaModifier.isModifiable('あ')) // cycle + small
        assertFalse(KanaModifier.isModifiable('ん'))
    }

    @Test
    fun `toggleCase flips ascii letters and ignores others`() {
        assertEquals('A', KanaModifier.toggleCase('a'))
        assertEquals('a', KanaModifier.toggleCase('A'))
        assertNull(KanaModifier.toggleCase('1'))
        assertNull(KanaModifier.toggleCase('あ'))
    }

    @Test
    fun `apply dispatches by flick direction`() {
        assertEquals('ば', KanaModifier.apply('は', Flick.CENTER))
        assertEquals('ば', KanaModifier.apply('は', Flick.LEFT))
        assertEquals('ぱ', KanaModifier.apply('は', Flick.RIGHT))
        assertEquals('ぁ', KanaModifier.apply('あ', Flick.UP))
        assertEquals('ぁ', KanaModifier.apply('あ', Flick.DOWN))
    }

    @Test
    fun `apply returns null when direction has no effect`() {
        assertNull(KanaModifier.apply('あ', Flick.LEFT))
        assertNull(KanaModifier.apply('あ', Flick.RIGHT))
    }
}
