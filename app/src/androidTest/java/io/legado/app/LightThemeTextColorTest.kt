package io.legado.app

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * #25 的实际控件回归:验证 API 26-29 的 XML 兜底和 API 30+ 资源覆盖失败时的亮色兜底。
 * 成功安装 ResourcesLoader 后的自定义色板由 AppThemeInstallerTest 的表/对比度测试覆盖;
 * 真机仍需检查实际 Activity 色板挂载、日夜切换及输入框/文件选择/删除弹窗截图。
 */
@RunWith(AndroidJUnit4::class)
class LightThemeTextColorTest {

    @Test
    fun lightInputAndDialogTitleDoNotResolveToWhitePlaceholders() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val appContext = ApplicationProvider.getApplicationContext<Context>()
            val configuration = Configuration(appContext.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            }
            val context = ContextThemeWrapper(
                appContext.createConfigurationContext(configuration),
                R.style.AppTheme_Light,
            )
            val surface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, 0)
            val dialogSurface = MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                surface,
            )
            val input = EditText(context).apply { setText("可读输入文字") }
            assertReadable("亮色输入框正文", input.currentTextColor, surface)

            val title = "删除书籍确认"
            val dialog = MaterialAlertDialogBuilder(context).setTitle(title).setMessage("确认删除?").create()
            try {
                dialog.create() // 只 inflate,不 show,无需窗口 token/用户操作。
                val titleView = findTextView(dialog.window!!.decorView, title)
                    ?: error("弹窗没有渲染标题")
                assertReadable("亮色删除弹窗标题", titleView.currentTextColor, dialogSurface)
            } finally {
                dialog.dismiss()
            }
        }
    }

    private fun findTextView(view: View, text: String): TextView? {
        if (view is TextView && view.text.toString() == text) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTextView(view.getChildAt(index), text)?.let { return it }
            }
        }
        return null
    }

    private fun assertReadable(message: String, foreground: Int, background: Int) {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val value = (color ushr shift and 0xFF) / 255.0
                return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        val fg = luminance(foreground)
        val bg = luminance(background)
        assertTrue(message, (max(fg, bg) + 0.05) / (min(fg, bg) + 0.05) >= 4.5)
    }
}
