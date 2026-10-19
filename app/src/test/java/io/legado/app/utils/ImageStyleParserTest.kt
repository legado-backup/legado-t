package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageStyleParserTest {

    @Test
    fun `parse keyword text`() {
        assertEquals(
            ImageStyleParser.ImageStyle.Text,
            ImageStyleParser.ImageStyle.parse("TEXT")
        )
    }

    @Test
    fun `parse keyword case insensitive`() {
        assertEquals(
            ImageStyleParser.ImageStyle.Full,
            ImageStyleParser.ImageStyle.parse(" full ")
        )
        assertEquals(
            ImageStyleParser.ImageStyle.Single,
            ImageStyleParser.ImageStyle.parse("Single")
        )
    }

    @Test
    fun `parse keyword default and auto`() {
        assertEquals(
            ImageStyleParser.ImageStyle.Default,
            ImageStyleParser.ImageStyle.parse("DEFAULT")
        )
        assertEquals(
            ImageStyleParser.ImageStyle.Default,
            ImageStyleParser.ImageStyle.parse("auto")
        )
    }

    @Test
    fun `parse width percent`() {
        val style = ImageStyleParser.ImageStyle.parse("width:50%") as ImageStyleParser.ImageStyle.Size
        assertEquals(50f, style.widthPercent)
        assertNull(style.heightPercent)
        assertNull(style.widthPx)
    }

    @Test
    fun `parse width px and dp`() {
        val px = ImageStyleParser.ImageStyle.parse("width:200px") as ImageStyleParser.ImageStyle.Size
        assertEquals(200f, px.widthPx)
        val dp = ImageStyleParser.ImageStyle.parse("width:200dp") as ImageStyleParser.ImageStyle.Size
        assertEquals(200f, dp.widthPx)
        val bare = ImageStyleParser.ImageStyle.parse("width:200") as ImageStyleParser.ImageStyle.Size
        assertEquals(200f, bare.widthPx)
    }

    @Test
    fun `parse height percent`() {
        val style =
            ImageStyleParser.ImageStyle.parse("height:30%") as ImageStyleParser.ImageStyle.Size
        assertEquals(30f, style.heightPercent)
        assertNull(style.widthPercent)
    }

    @Test
    fun `parse width and height with auto`() {
        val style =
            ImageStyleParser.ImageStyle.parse("width:100%;height:auto") as ImageStyleParser.ImageStyle.Size
        assertEquals(100f, style.widthPercent)
        assertTrue(!style.hasHeight)
    }

    @Test
    fun `parse multiple declarations`() {
        val style =
            ImageStyleParser.ImageStyle.parse("width:50%; height:200dp") as ImageStyleParser.ImageStyle.Size
        assertEquals(50f, style.widthPercent)
        assertEquals(200f, style.heightPx)
    }

    @Test
    fun `parse invalid style returns null`() {
        assertNull(ImageStyleParser.ImageStyle.parse(null))
        assertNull(ImageStyleParser.ImageStyle.parse(""))
        assertNull(ImageStyleParser.ImageStyle.parse("   "))
        assertNull(ImageStyleParser.ImageStyle.parse("width:auto"))
        assertNull(ImageStyleParser.ImageStyle.parse("bogus"))
        assertNull(ImageStyleParser.ImageStyle.parse("width"))
    }

    @Test
    fun `from src without option returns null`() {
        assertNull(ImageStyleParser.ImageStyle.fromSrc("https://example.test/a.jpg"))
    }

    @Test
    fun `from src with style option`() {
        val style = ImageStyleParser.ImageStyle.fromSrc(
            "https://example.test/a.svg,{\"style\":\"TEXT\"}"
        )
        assertEquals(ImageStyleParser.ImageStyle.Text, style)
    }

    @Test
    fun `from src with other option but no style returns null`() {
        assertNull(
            ImageStyleParser.ImageStyle.fromSrc(
                "https://example.test/a.svg,{\"js\":\"getSvg()\"}"
            )
        )
    }

    @Test
    fun `from src with style size option`() {
        val style = ImageStyleParser.ImageStyle.fromSrc(
            "https://example.test/a.jpg,{\"style\":\"width:50%\",\"js\":\"x\"}"
        ) as ImageStyleParser.ImageStyle.Size
        assertEquals(50f, style.widthPercent)
    }

    @Test
    fun `keyword property mapping`() {
        assertEquals("TEXT", ImageStyleParser.ImageStyle.Text.keyword)
        assertEquals("FULL", ImageStyleParser.ImageStyle.Full.keyword)
        assertEquals("SINGLE", ImageStyleParser.ImageStyle.Single.keyword)
        assertEquals("DEFAULT", ImageStyleParser.ImageStyle.Default.keyword)
        assertNull(ImageStyleParser.ImageStyle.Size(widthPercent = 50f).keyword)
    }
}
