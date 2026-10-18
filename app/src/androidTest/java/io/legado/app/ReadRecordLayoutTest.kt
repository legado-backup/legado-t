package io.legado.app

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.databinding.ItemReadRecordBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ReadRecordLayoutTest {
    @Test
    fun recordRowsDoNotOverlapCoverOrEachOtherAtLargeFont() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val app = ApplicationProvider.getApplicationContext<Context>()
            for (language in listOf("zh", "en")) {
                val configuration = Configuration(app.resources.configuration).apply {
                    setLocale(Locale(language))
                    fontScale = 1.5f
                }
                val context = ContextThemeWrapper(app.createConfigurationContext(configuration), R.style.AppTheme_Light)
                val binding = ItemReadRecordBinding.inflate(LayoutInflater.from(context))
                binding.tvBookName.text = "Very long book title 很长的书名"
                binding.tvAuthor.text = "An author 作者名字"
                binding.tvReadingTime.text = "123天23小时59分钟59秒"
                binding.tvLastReadTime.text = "2026-10-18"
                val width = (360 * context.resources.displayMetrics.density).toInt()
                binding.root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
                binding.root.layout(0, 0, width, binding.root.measuredHeight)
                assertEquals(binding.tvReadingTimeTag.top, binding.tvReadingTime.top)
                assertEquals(binding.tvLastReadTimeTag.top, binding.tvLastReadTime.top)
                assertTrue(binding.tvLastReadTime.top >= binding.tvReadingTime.bottom)
                assertTrue(binding.tvReadingTimeTag.left >= binding.ivCover.right)
                assertTrue(binding.tvLastReadTimeTag.left >= binding.ivCover.right)
                assertTrue(binding.root.height >= binding.tvLastReadTime.bottom)
            }
        }
    }
}
