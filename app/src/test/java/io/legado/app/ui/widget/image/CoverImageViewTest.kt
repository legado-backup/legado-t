package io.legado.app.ui.widget.image

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class CoverImageViewTest {

    @Test
    fun `transparent cover pixels use the current page background`() {
        val source = projectFile("src/main/java/io/legado/app/ui/widget/image/CoverImageView.kt").readText()

        assertTrue(source.contains("import io.legado.app.lib.theme.backgroundColor"))
        assertTrue(source.contains("setBackgroundColor(context.backgroundColor)"))
    }

    @Test
    fun `rounded clip surrounds the entire draw and restores the canvas`() {
        val source = projectFile("src/main/java/io/legado/app/ui/widget/image/CoverImageView.kt").readText()
        val draw = source.substringAfter("override fun draw(canvas: Canvas)")
            .substringBefore("override fun onDraw(canvas: Canvas)")
        val onDraw = source.substringAfter("override fun onDraw(canvas: Canvas)")
            .substringBefore("private fun drawNameAuthor")

        assertTrue("裁剪必须从 draw 开始,否则漏掉 View 的 background", draw.contains("canvas.clipPath(filletPath)"))
        assertTrue(draw.indexOf("canvas.save()") < draw.indexOf("canvas.clipPath(filletPath)"))
        assertTrue(draw.substringAfter("canvas.clipPath(filletPath)").contains("super.draw(canvas)"))
        assertTrue("不能污染后续兄弟视图绘制", draw.contains("finally"))
        assertTrue(draw.contains("canvas.restoreToCount(saveCount)"))
        assertFalse("不要退回只裁图片而漏裁背景", onDraw.contains("canvas.clipPath"))
    }

    @Test
    fun `cover cards own one clip without static white fill or corner padding`() {
        val appNs = "http://schemas.android.com/apk/res-auto"
        val androidNs = "http://schemas.android.com/apk/res/android"
        listOf("item_bookshelf_grid", "item_bookshelf_grid_group", "item_explore_cover").forEach { name ->
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val document = factory.newDocumentBuilder().parse(projectFile("src/main/res/layout/$name.xml"))
            val card = document.getElementsByTagName("com.google.android.material.card.MaterialCardView")
                .item(0) as org.w3c.dom.Element
            val cover = document.getElementsByTagName("io.legado.app.ui.widget.image.CoverImageView")
                .item(0) as org.w3c.dom.Element

            assertTrue("$name 封面卡不得闪现静态白底", card.getAttributeNS(appNs, "cardBackgroundColor") == "@android:color/transparent")
            assertTrue("$name 必须让封面贴到边缘", card.getAttributeNS(appNs, "cardPreventCornerOverlap") == "false")
            assertTrue(card.getAttributeNS(appNs, "cardUseCompatPadding") == "false")
            assertTrue(card.getAttributeNS(androidNs, "clipToOutline") == "true")
            assertTrue("$name 由卡片统一裁剪,子封面不要再裁一次", cover.getAttributeNS(appNs, "coverCornerRadius") == "0dp")
        }
    }

    @Test
    fun `only cover adapters use page matched cover card fill`() {
        listOf(
            "ui/main/bookshelf/BooksAdapterHelper.kt",
            "ui/main/bookshelf/style2/BooksAdapter.kt",
            "ui/main/explore/ExploreCoverAdapter.kt",
        ).forEach { path ->
            val source = projectFile("src/main/java/io/legado/app/$path").readText()
            assertTrue("$path 的封面卡仍依赖静态资源底色", source.contains(".coverCardBackgroundColor"))
            assertFalse(source.contains(".cardBackgroundColor"))
        }
    }

    private fun projectFile(pathInApp: String): File {
        return listOf(File(pathInApp), File("app/$pathInApp")).first { it.exists() }
    }
}
