package io.legado.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.card.MaterialCardView
import io.legado.app.ui.widget.image.CoverImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #36 的实际像素回归。请在 API 26、29、30+ 设备分别运行;软件 Canvas 覆盖 View.draw 的
 * 背景/图片/foreground 顺序,MaterialCardView 硬件 outline 和厂商抗锯齿仍需截图验收。
 * 不走 Glide/网络,避免图片加载时序影响结果。
 */
@RunWith(AndroidJUnit4::class)
class CoverImageViewDrawingTest {

    @Test
    fun roundedCoverClipsBackgroundImageAndForeground() = onMain {
        val cover = CoverImageView(context()).apply {
            setBackgroundColor(Color.WHITE)
            setImageDrawable(ColorDrawable(Color.RED))
            foreground = ColorDrawable(Color.BLUE)
        }
        val parentColor = 0xFF23313D.toInt()
        val output = render(cover, parentColor)

        assertCorners(output, parentColor)
        assertEquals(Color.BLUE, output.getPixel(WIDTH / 2, HEIGHT / 2))
    }

    @Test
    fun transparentPixelsKeepCustomBackgroundButRoundedOutsideKeepsParent() = onMain {
        listOf(0xFFF2E7CF.toInt(), 0xFF23313D.toInt()).forEach { customBackground ->
            val transparentImage = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            Canvas(transparentImage).drawRect(30f, 40f, 70f, 100f, Paint().apply { color = Color.RED })
            val cover = CoverImageView(context()).apply {
                setBackgroundColor(customBackground)
                setImageBitmap(transparentImage)
            }
            val output = render(cover, Color.MAGENTA)

            assertCorners(output, Color.MAGENTA)
            assertEquals(customBackground, output.getPixel(WIDTH / 2, 2))
            assertEquals(Color.RED, output.getPixel(WIDTH / 2, HEIGHT / 2))
        }
    }

    @Test
    fun roundedCoverRestoresCanvasForTheNextView() = onMain {
        val cover = CoverImageView(context()).apply { setImageDrawable(ColorDrawable(Color.RED)) }
        measureAndLayout(cover)
        val output = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        cover.draw(canvas)
        canvas.drawColor(Color.GREEN)

        assertCorners(output, Color.GREEN)
        assertEquals(Color.GREEN, output.getPixel(WIDTH / 2, HEIGHT / 2))
    }

    @Test
    fun parentCardOwnsGridClipWithoutDoubleRoundingOrCornerInsets() = onMain {
        listOf(R.layout.item_bookshelf_grid, R.layout.item_bookshelf_grid_group, R.layout.item_explore_cover)
            .forEach { layout ->
                val root = LayoutInflater.from(context()).inflate(layout, null)
                val card = root.findViewById<MaterialCardView>(R.id.cover_card)
                assertFalse(card.preventCornerOverlap)
                assertFalse(card.useCompatPadding)
                assertTrue(card.clipToOutline)
                assertEquals(Color.TRANSPARENT, card.cardBackgroundColor.defaultColor)

                val cover = root.findViewById<CoverImageView>(R.id.iv_cover).apply {
                    foreground = null
                    setImageDrawable(ColorDrawable(Color.RED))
                }
                // coverCornerRadius=0:子视图画满矩形,只由上面的 MaterialCardView 裁一次。
                val output = render(cover, Color.MAGENTA)
                assertCorners(output, Color.RED)
            }
    }

    private fun context(): Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(),
        R.style.AppTheme_Light,
    )

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { block() }
    }

    private fun measureAndLayout(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, WIDTH, HEIGHT)
    }

    private fun render(cover: CoverImageView, parentColor: Int): Bitmap {
        measureAndLayout(cover)
        val output = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(parentColor)
        cover.draw(canvas)
        return output
    }

    private fun assertCorners(bitmap: Bitmap, color: Int) {
        assertEquals(color, bitmap.getPixel(0, 0))
        assertEquals(color, bitmap.getPixel(WIDTH - 1, 0))
        assertEquals(color, bitmap.getPixel(0, HEIGHT - 1))
        assertEquals(color, bitmap.getPixel(WIDTH - 1, HEIGHT - 1))
    }

    private companion object {
        const val WIDTH = 100
        const val HEIGHT = 140
    }
}
