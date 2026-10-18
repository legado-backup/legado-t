package io.legado.app.lib.theme

import io.legado.app.R
import io.legado.app.lib.theme.arsc.ArscTableReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class AppThemeInstallerTest {

    private val scheme = AppColorScheme.buildScheme(
        seed = 0xFF3D7EFF.toInt(),
        isDark = false,
        surfaceAnchor = 0xFFFAFAFA.toInt(),
        isEInk = false,
        fidelityPrimary = true,
    )

    @Test
    fun `runtime table covers the complete app token set`() {
        val colors = AppThemeInstaller.colorResourceOverrides(scheme)

        assertEquals(scheme.primary, colors[R.color.m3_primary])
        assertEquals(scheme.onPrimary, colors[R.color.m3_on_primary])
        assertEquals(scheme.secondaryContainer, colors[R.color.m3_secondary_container])
        assertEquals(scheme.onSecondaryContainer, colors[R.color.m3_on_secondary_container])
        assertEquals(scheme.error, colors[R.color.m3_error])
        assertEquals(scheme.outline, colors[R.color.m3_outline])
        assertEquals(scheme.surface, colors[R.color.m3_surface])
        assertEquals(scheme.onSurface, colors[R.color.m3_on_surface])
        assertEquals(scheme.surfaceContainerHighest, colors[R.color.m3_surface_container_highest])
        assertEquals(scheme.surfaceBright, colors[R.color.m3_surface_bright])
        assertEquals(scheme.surfaceDim, colors[R.color.m3_surface_dim])
        assertEquals(scheme.primary, colors[R.color.accent])
        assertEquals(scheme.background, colors[R.color.background])
        assertEquals(scheme.surfaceContainerLow, colors[R.color.background_card])
        assertEquals(scheme.surfaceContainerLow, colors[R.color.background_popup_menu])
        assertEquals(scheme.error, colors[R.color.error])
        // primaryText and secondaryText are intentionally not overridden:
        // 118+ 布局直引、背景上下文多样,维持 colors.xml 静态值以保证对比度契约
        assertEquals(null, colors[R.color.primaryText])
        assertEquals(null, colors[R.color.secondaryText])
        // 34 m3 token + 5 legacy alias
        assertEquals(39, colors.size)
    }

    @Test
    fun `light theme text tokens stay readable instead of reverting to white placeholders`() {
        listOf(0xFFFAFAFA.toInt(), 0xFFF2E7CF.toInt(), 0xFFE3F2FD.toInt()).forEach { anchor ->
            val light = AppColorScheme.buildScheme(
                seed = 0xFF3D7EFF.toInt(),
                isDark = false,
                surfaceAnchor = anchor,
                isEInk = false,
                fidelityPrimary = true,
            )
            val colors = AppThemeInstaller.colorResourceOverrides(light)
            val primaryText = colors.getValue(R.color.m3_on_surface)
            val secondaryText = colors.getValue(R.color.m3_on_surface_variant)
            val surfaces = listOf(light.surface, light.surfaceContainerHigh)
            surfaces.forEach { surface ->
                assertTrue("日间输入框/弹窗主文字不能变白", contrast(primaryText, surface) >= 4.5)
                assertTrue("日间提示/次要文字必须可读", contrast(secondaryText, surface) >= 4.5)
            }
        }
    }

    @Test
    fun `table bytes round trip with the real token ids`() {
        val colors = AppThemeInstaller.colorResourceOverrides(scheme)
        val bytes = AppThemeInstaller.colorResourcesTable(
            packageName = "io.legado.app",
            entryNames = { "token_${it and 0xFFFF}" },
            scheme = scheme,
        )

        val table = ArscTableReader(bytes).readTable()
        assertEquals(1, table.packages.size)
        val pkg = table.packages[0]
        assertEquals("io.legado.app", pkg.name)
        assertEquals(colors, pkg.entries)
        assertEquals(colors.size, pkg.keyNames.size)
    }

    private fun contrast(first: Int, second: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val value = (color ushr shift and 0xFF) / 255.0
                return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }
}
