package io.legado.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlaySpeedTest {

    @Test
    fun `clamp keeps speed within player bounds`() {
        assertEquals(0.5f, AudioPlaySpeed.clamp(0f), 0f)
        assertEquals(0.5f, AudioPlaySpeed.clamp(-1f), 0f)
        assertEquals(1.25f, AudioPlaySpeed.clamp(1.25f), 0f)
        assertEquals(3f, AudioPlaySpeed.clamp(4f), 0f)
    }

    @Test
    fun `clamp replaces non finite saved values with normal speed`() {
        assertEquals(1f, AudioPlaySpeed.clamp(Float.NaN), 0f)
        assertEquals(1f, AudioPlaySpeed.clamp(Float.POSITIVE_INFINITY), 0f)
        assertEquals(1f, AudioPlaySpeed.clamp(Float.NEGATIVE_INFINITY), 0f)
    }

    @Test
    fun `incremental adjustment uses the same bounds`() {
        assertEquals(0.5f, AudioPlaySpeed.adjust(0.5f, -0.1f), 0f)
        assertEquals(3f, AudioPlaySpeed.adjust(2.95f, 0.1f), 0f)
        assertEquals(1.1f, AudioPlaySpeed.adjust(1f, 0.1f), 0.0001f)
    }

    @Test
    fun `presets cover required playback rates`() {
        assertEquals(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f), AudioPlaySpeed.presets)
        assertTrue(AudioPlaySpeed.presets.all { it in AudioPlaySpeed.MIN..AudioPlaySpeed.MAX })
        assertFalse(AudioPlaySpeed.presets.isEmpty())
    }

    @Test
    fun `selector marks nearest preset for incremental speeds`() {
        assertEquals(2, AudioPlaySpeed.presetIndex(1f))
        assertEquals(2, AudioPlaySpeed.presetIndex(1.1f))
        assertEquals(0, AudioPlaySpeed.presetIndex(0.25f))
        assertEquals(6, AudioPlaySpeed.presetIndex(3.5f))
    }

    @Test
    fun `preset label omits unnecessary decimal zero`() {
        assertEquals("1", AudioPlaySpeed.format(1f))
        assertEquals("0.75", AudioPlaySpeed.format(0.75f))
        assertEquals("3", AudioPlaySpeed.format(4f))
    }
}
