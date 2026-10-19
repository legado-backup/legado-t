@file:Suppress("unused")

package io.legado.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Picture
import android.os.Build
import android.text.Html
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EdgeEffect
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.core.graphics.record
import androidx.core.graphics.withTranslation
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.widget.ViewPager2
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.TintHelper
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.utils.canvasrecorder.CanvasRecorder
import io.legado.app.utils.canvasrecorder.record
import splitties.systemservices.inputMethodManager
import splitties.views.bottomPadding
import splitties.views.topPadding


private tailrec fun getCompatActivity(context: Context?): AppCompatActivity? {
    return when (context) {
        is AppCompatActivity -> context
        is androidx.appcompat.view.ContextThemeWrapper -> getCompatActivity(context.baseContext)
        is android.view.ContextThemeWrapper -> getCompatActivity(context.baseContext)
        else -> null
    }
}

val View.activity: AppCompatActivity?
    get() = getCompatActivity(context)

fun View.hideSoftInput() = run {
    inputMethodManager.hideSoftInputFromWindow(this.windowToken, 0)
}

@Suppress("DEPRECATION")
fun EditText.showSoftInput() = run {
    requestFocus()
    inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}

fun View.disableAutoFill() = run {
    this.importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
}

fun View.applyTint(
    @ColorInt color: Int,
    isDark: Boolean = AppConfig.isNightTheme
) {
    TintHelper.setTintAuto(this, color, false, isDark)
}

fun View.applyBackgroundTint(
    @ColorInt color: Int,
    isDark: Boolean = AppConfig.isNightTheme
) {
    if (background == null) {
        setBackgroundColor(color)
    } else {
        TintHelper.setTintAuto(this, color, true, isDark)
    }
}

fun RecyclerView.setEdgeEffectColor(@ColorInt color: Int) {
    edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
        override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
            val edgeEffect = super.createEdgeEffect(view, direction)
            edgeEffect.color = color
            return edgeEffect
        }
    }
}

fun ViewPager.setEdgeEffectColor(@ColorInt color: Int) {
    try {
        val clazz = ViewPager::class.java
        for (name in arrayOf("mLeftEdge", "mRightEdge")) {
            val field = clazz.getDeclaredField(name)
            field.isAccessible = true
            val edge = field.get(this)
            (edge as EdgeEffect).color = color
        }
    } catch (ignored: Exception) {
    }
}

fun ViewPager2.setEdgeEffectColor(@ColorInt color: Int) {
    (getChildAt(0) as? RecyclerView)?.setEdgeEffectColor(color)
}

fun EditText.disableEdit() {
    keyListener = null
}

fun View.gone() {
    if (visibility != GONE) {
        visibility = GONE
    }
}

fun View.gone(gone: Boolean) {
    if (gone) {
        gone()
    } else {
        visibility = VISIBLE
    }
}

fun View.invisible() {
    if (visibility != INVISIBLE) {
        visibility = INVISIBLE
    }
}

fun View.visible() {
    if (visibility != VISIBLE) {
        visibility = VISIBLE
    }
}

fun View.visible(visible: Boolean) {
    if (visible && !isVisible) {
        visibility = VISIBLE
    } else if (!visible && isVisible) {
        visibility = INVISIBLE
    }
}

fun View.screenshot(bitmap: Bitmap? = null, canvas: Canvas? = null): Bitmap? {
    return if (width > 0 && height > 0) {
        val screenshot = if (bitmap != null && bitmap.width == width && bitmap.height == height) {
            bitmap.eraseColor(Color.TRANSPARENT)
            bitmap
        } else {
            bitmap?.recycle()
            createBitmap(width, height)
        }
        val c = canvas ?: Canvas()
        c.setBitmap(screenshot)
        c.withTranslation(-scrollX.toFloat(), -scrollY.toFloat()) {
            this@screenshot.draw(c)
        }
        c.setBitmap(null)
        screenshot.prepareToDraw()
        screenshot
    } else {
        null
    }
}

fun View.screenshot(picture: Picture) {
    if (width > 0 && height > 0) {
        picture.record(width, height) {
            withTranslation(-scrollX.toFloat(), -scrollY.toFloat()) {
                draw(this)
            }
        }
    }
}

fun View.screenshot(canvasRecorder: CanvasRecorder) {
    if (width > 0 && height > 0) {
        canvasRecorder.record(width, height) {
            draw(this)
        }
    }
}

fun View.setPaddingBottom(bottom: Int) {
    setPadding(paddingLeft, paddingTop, paddingRight, bottom)
}

fun SeekBar.progressAdd(int: Int) {
    progress += int
}

fun RadioGroup.getIndexById(id: Int): Int {
    for (i in 0 until this.childCount) {
        if (id == get(i).id) {
            return i
        }
    }
    return 0
}

fun RadioGroup.getCheckedIndex(): Int {
    for (i in 0 until this.childCount) {
        if (checkedRadioButtonId == get(i).id) {
            return i
        }
    }
    return 0
}

fun RadioGroup.checkByIndex(index: Int) {
    check(get(index).id)
}

@SuppressLint("ObsoleteSdkInt")
fun TextView.setHtml(html: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
    } else {
        @Suppress("DEPRECATION")
        text = Html.fromHtml(html)
    }
}

fun TextView.setTextIfNotEqual(charSequence: CharSequence?) {
    if (text != charSequence) {
        text = charSequence
    }
}

fun View.shouldHideSoftInput(event: MotionEvent): Boolean {
    if (this is EditText) {
        val l = intArrayOf(0, 0)
        getLocationInWindow(l)
        val left = l[0]
        val top = l[1]
        val bottom = top + getHeight()
        val right = left + getWidth()
        return !(event.x > left && event.x < right && event.y > top && event.y < bottom)
    }
    return false
}

fun View.applyStatusBarPadding(withInitialPadding: Boolean = false) {
    val initialPadding = if (withInitialPadding) topPadding else 0
    setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
        topPadding = initialPadding + insets.top
        windowInsets
    }
}

fun View.applyNavigationBarPadding(withInitialPadding: Boolean = false) {
    val initialPadding = if (withInitialPadding) bottomPadding else 0
    setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
        bottomPadding = initialPadding + windowInsets.navigationBarHeight
        windowInsets
    }
}

fun View.applyNavigationBarMargin(withInitialMargin: Boolean = false) {
    val initialMargin = if (withInitialMargin) marginBottom else 0
    setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
        updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = initialMargin + windowInsets.navigationBarHeight
        }
        windowInsets
    }
}

fun View.setBackgroundKeepPadding(@DrawableRes backgroundResId: Int) {
    val paddingLeft = paddingLeft
    val paddingTop = paddingTop
    val paddingRight = paddingRight
    val paddingBottom = paddingBottom
    setBackgroundResource(backgroundResId)
    setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
}

fun View.canScroll(direction: Int): Boolean {
    return canScrollVertically(direction) || canScrollHorizontally(direction)
}

private val requestLayoutBroken = Build.VERSION.SDK_INT in Build.VERSION_CODES.O..Build.VERSION_CODES.Q

fun View.setOnApplyWindowInsetsListenerCompat(listener: (View, WindowInsetsCompat) -> WindowInsetsCompat) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val windowInsets = listener(view, insets)
        if (requestLayoutBroken && isLayoutRequested) {
            post {
                requestLayout()
            }
        }
        windowInsets
    }
}

/**
 * 阅读底部面板文字指示器的描边外观:radius_m 圆角 + 1dp 描边,
 * 描边/文字色按 bottomBackground 亮暗取主文字色,按压=transparent30,禁用=md_grey_500
 * (原 StrokeTextView isBottomBackground 分支的等价复刻,选中态腿为死代码未保留)。
 */
fun TextView.applyBottomPanelStrokeStyle() {
    val isLight = ColorUtils.isColorLight(context.bottomBackground)
    val textColor = context.getPrimaryTextColor(isLight)
    background = Selector.shapeBuild()
        .setCornerRadius(context.resources.getDimensionPixelOffset(io.legado.app.R.dimen.radius_m))
        .setStrokeWidth(1.dpToPx())
        .setDisabledStrokeColor(context.getCompatColor(io.legado.app.R.color.md_grey_500))
        .setDefaultStrokeColor(textColor)
        .setPressedBgColor(context.getCompatColor(io.legado.app.R.color.transparent30))
        .create()
    setTextColor(
        Selector.colorBuild()
            .setDefaultColor(textColor)
            .setDisabledColor(context.getCompatColor(io.legado.app.R.color.md_grey_500))
            .create()
    )
}

/**
 * 给视图设置“圆角 + 指定颜色”的纯色背景，
 * 替代直接 setBackgroundColor 造成的方角，使对话框与圆角卡片一致。
 * @param topOnly 仅圆顶部两角（用于对话框 Toolbar）；false 时四角全圆。
 * @param radius 圆角半径 px，默认 12dp；对话框模板传 radius_xl 与 AlertDialog 对齐。
 */
fun View.setRoundBackground(color: Int, topOnly: Boolean = false, radius: Float = 12f.dpToPx()) {
    val drawable = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        if (topOnly) {
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        } else {
            cornerRadius = radius
        }
        setColor(color)
    }
    background = drawable
}


/**
 * 降低 ViewPager2 的横/纵向拖动灵敏度:把内部 RecyclerView 的 touchSlop 放大数倍,
 * 令翻页需要更明确的拖动距离。用于主 tab pager,避免书架分组/发现横滑时轻微横移即误切标签。
 * 反射改私有 mTouchSlop,失败静默(不影响功能,只是维持默认灵敏度)。
 */
fun ViewPager2.reduceDragSensitivity(factor: Int = 4) {
    runCatching {
        val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
        recyclerViewField.isAccessible = true
        val recyclerView = recyclerViewField.get(this) as RecyclerView
        val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
        touchSlopField.isAccessible = true
        val touchSlop = touchSlopField.get(recyclerView) as Int
        touchSlopField.set(recyclerView, touchSlop * factor)
    }
}
