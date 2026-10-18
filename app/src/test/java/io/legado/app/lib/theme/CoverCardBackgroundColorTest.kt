package io.legado.app.lib.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverCardBackgroundColorTest {

    @Test
    fun `non immersive cover fill matches arbitrary page color not static card white`() {
        listOf(
            0xFFF2E7CF.toInt(), // 自定义米色
            0xFF23313D.toInt(), // 自定义深色
            0xFFFAFAFA.toInt(),
            0xFFFFFFFF.toInt(),
        ).forEach { background ->
            assertEquals(background, coverCardFillColor(background, immersive = false))
        }
    }

    @Test
    fun `immersive cover fill remains transparent`() {
        assertEquals(0, coverCardFillColor(0xFFF2E7CF.toInt(), immersive = true))
        assertEquals(0, coverCardFillColor(0xFF23313D.toInt(), immersive = true))
    }
}
