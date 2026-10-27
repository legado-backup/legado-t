package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import android.view.animation.Animation
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.slider.Slider
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ViewReadMenuBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.source.getSourceType
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.buttonDisabledColor
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.model.ReadBook
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.ui.login.hasSourceLoginConfiguration
import io.legado.app.ui.widget.PopupAction
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyAppTint
import io.legado.app.utils.ConstraintModify
import io.legado.app.utils.activity
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.loadAnimation
import io.legado.app.utils.modifyBegin
import io.legado.app.utils.openUrl
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.startActivity
import io.legado.app.utils.visible
import splitties.views.onClick
import splitties.views.onLongClick

/**
 * 阅读界面菜单
 */
class ReadMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    var canShowMenu: Boolean = false
    private val callBack: CallBack get() = activity as CallBack
    private val binding = ViewReadMenuBinding.inflate(LayoutInflater.from(context), this, true)
    private var confirmSkipToChapter: Boolean = false
    private var isMenuOutAnimating = false
    private val menuTopIn: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_top_in)
    }
    private val menuTopOut: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_top_out)
    }
    private val menuBottomIn: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_bottom_in)
    }
    private val menuBottomOut: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_bottom_out)
    }
    private val immersiveMenu: Boolean
        get() = AppConfig.readBarStyleFollowPage && ReadBookConfig.durConfig.curBgType() == 0
    private var bgColor: Int = if (immersiveMenu) {
        kotlin.runCatching {
            Color.parseColor(ReadBookConfig.durConfig.curBgStr())
        }.getOrDefault(context.bottomBackground)
    } else {
        context.bottomBackground
    }
    private var textColor: Int = if (immersiveMenu) {
        ReadBookConfig.durConfig.curTextColor()
    } else {
        context.getPrimaryTextColor(ColorUtils.isColorLight(bgColor))
    }

    private var bottomBackgroundList: ColorStateList = Selector.colorBuild()
        .setDefaultColor(bgColor)
        .setPressedColor(ColorUtils.darkenColor(bgColor))
        .create()
    private var onMenuOutEnd: (() -> Unit)? = null
    private val showBrightnessView
        get() = context.getPrefBoolean(
            PreferKey.showBrightnessView,
            true
        )
    private val sourceActionMenu by lazy {
        PopupAction(context).apply {
            setVertical(true)
            setDangerValues(setOf("disableSource"))
            onActionClick = { action ->
                when (action) {
                    "login" -> callBack.showLogin()
                    "chapterPay" -> callBack.payAction()
                    "editSource" -> callBack.openSourceEditActivity()
                    "disableSource" -> callBack.disableSource()
                }
                dismiss()
            }
        }
    }
    private val menuInListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            binding.tvSourceAction.text =
                ReadBook.bookSource?.bookSourceName ?: context.getString(R.string.book_source)
            binding.tvSourceAction.isGone = ReadBook.isLocalBook
            ReadBook.bookSource?.let {
                if (it.customButton) {
                    binding.tvCustomBtn.visibility = VISIBLE
                }
            }
            callBack.upSystemUiVisibility()
            binding.llBrightness.visible(showBrightnessView)
        }

        @SuppressLint("RtlHardcoded")
        override fun onAnimationEnd(animation: Animation) {
            binding.vwMenuBg.setOnClickListener { runMenuOut() }
            callBack.upSystemUiVisibility()
            if (!LocalConfig.readMenuHelpVersionIsLast) {
                callBack.showHelp()
            }
        }

        override fun onAnimationRepeat(animation: Animation) = Unit
    }
    private val menuOutListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            isMenuOutAnimating = true
            binding.vwMenuBg.setOnClickListener(null)
        }

        override fun onAnimationEnd(animation: Animation) {
            this@ReadMenu.invisible()
            binding.titleBar.invisible()
            binding.bottomMenu.invisible()
            canShowMenu = false
            isMenuOutAnimating = false
            onMenuOutEnd?.invoke()
            callBack.upSystemUiVisibility()
        }

        override fun onAnimationRepeat(animation: Animation) = Unit
    }

    init {
        initView()
        upBrightnessState()
        bindEvent()
    }

    private fun initView(reset: Boolean = false) = binding.run {
        if (AppConfig.isNightTheme) {
            fabNightTheme.setIconResource(R.drawable.ic_daytime)
        } else {
            fabNightTheme.setIconResource(R.drawable.ic_brightness)
        }
        initAnimation()
        tvCustomBtn.setColorFilter(context.accentColor)
        // 本菜单完全自管 titleBar 前景色，禁止其因全局沉浸式设置自动按页面背景着色
        // （否则非沉浸菜单的书名会被染成与章节名相反的颜色）
        titleBar.disableAutoForegroundColor()
        if (immersiveMenu) {
            val lightTextColor = ColorUtils.withAlpha(ColorUtils.lightenColor(textColor), 0.75f)
            titleBar.setTextColor(textColor)
            titleBar.setBackgroundColor(bgColor)
            titleBar.setColorFilter(textColor)
            tvChapterName.setTextColor(lightTextColor)
            tvChapterUrl.setTextColor(lightTextColor)
        } else {
            val bgColor = context.primaryColor
            val textColor = context.primaryTextColor
            titleBar.setTextColor(textColor)
            titleBar.setBackgroundColor(bgColor)
            titleBar.setColorFilter(textColor)
            tvChapterName.setTextColor(textColor)
            tvChapterUrl.setTextColor(textColor)
        }
        val brightnessBackground = GradientDrawable()
        brightnessBackground.cornerRadius = resources.getDimension(R.dimen.radius_l)
        brightnessBackground.setColor(ColorUtils.adjustAlpha(bgColor, 0.5f))
        llBrightness.background = brightnessBackground
        if (AppConfig.isEInkMode) {
            titleBar.setBackgroundResource(R.drawable.bg_eink_border_bottom)
            llBottomBg.setBackgroundResource(R.drawable.bg_eink_border_top)
        } else {
            val radius = resources.getDimension(R.dimen.radius_l)
            llBottomBg.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
                setColor(bgColor)
            }
        }
        fabSearch.setTint(textColor)
        fabAutoPage.setTint(textColor)
        fabReplaceRule.setTint(textColor)
        fabNightTheme.setTint(textColor)
        // 禁用态颜色从随底栏自适应的 textColor 派生(降透明度),而非全局主题的固定灰
        // (buttonDisabledColor 跟随 primaryColor 判明暗,与阅读底色不一致时会在深色背景下近乎不可见)
        val chapterTextColor = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(ColorUtils.withAlpha(textColor, 0.4f), textColor)
        )
        tvPre.setTextColor(chapterTextColor)
        tvNext.setTextColor(chapterTextColor)
        llCatalog.setTint(textColor)
        llReadAloud.setTint(textColor)
        llFont.setTint(textColor)
        llSetting.setTint(textColor)
        vwBrightnessPosAdjust.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
        llBrightness.setOnClickListener(null)
        seekBrightness.slider.applyAppTint(context.accentColor)
        seekBrightness.slider.post {
            seekBrightness.slider.value = AppConfig.readBrightness.toFloat()
                .coerceIn(seekBrightness.slider.valueFrom, seekBrightness.slider.valueTo)
        }
        // 进度滑杆走主题强调色通道(与亮度滑杆、换肤引擎 Slider 默认同源),不随底栏前景 textColor
        seekReadPage.applyAppTint(context.accentColor)
        if (AppConfig.showReadTitleBarAddition) {
            titleBarAddition.visible()
        } else {
            titleBarAddition.gone()
        }
        upBrightnessVwPos()
        /**
         * 确保视图不被导航栏遮挡
         */
        applyNavigationBarPadding()
    }

    fun reset() {
        upColorConfig()
        initView(true)
    }

    fun refreshMenuColorFilter() {
        // 用顶部 toolbar 实际背景对应的前景色给返回箭头和菜单图标（换源/刷新/下载等）着色，
        // 覆盖 getMenuColor 的结果（沉浸操作栏开启时它会误判栏透明、按页面背景着色，深色主色调下得到黑图标）。
        // 注意：成员 textColor 基准是 bottomBackground（底栏），与顶部 toolbar 背景不同，
        // 非沉浸时须用 primaryTextColor（基准 = primaryColor，即顶部 toolbar 的实际背景）。
        // post 到下一帧：本方法在 onCompatCreateOptionsMenu 里调用，需晚于其后 BaseActivity 的 applyTint 才不被覆盖。
        val toolbarTextColor = if (immersiveMenu) textColor else context.primaryTextColor
        binding.titleBar.post {
            binding.titleBar.setColorFilter(toolbarTextColor)
        }
    }

    private fun upColorConfig() {
        bgColor = if (immersiveMenu) {
            kotlin.runCatching {
                Color.parseColor(ReadBookConfig.durConfig.curBgStr())
            }.getOrDefault(context.bottomBackground)
        } else {
            context.bottomBackground
        }
        textColor = if (immersiveMenu) {
            ReadBookConfig.durConfig.curTextColor()
        } else {
            context.getPrimaryTextColor(ColorUtils.isColorLight(bgColor))
        }
        bottomBackgroundList = Selector.colorBuild()
            .setDefaultColor(bgColor)
            .setPressedColor(ColorUtils.darkenColor(bgColor))
            .create()
    }

    fun upBrightnessState() {
        if (brightnessAuto()) {
            binding.ivBrightnessAuto.setColorFilter(context.accentColor)
            binding.seekBrightness.slider.isEnabled = false
        } else {
            binding.ivBrightnessAuto.setColorFilter(context.buttonDisabledColor)
            binding.seekBrightness.slider.isEnabled = true
        }
        setScreenBrightness(AppConfig.readBrightness.toFloat())
    }

    /**
     * 设置屏幕亮度
     */
    fun setScreenBrightness(value: Float) {
        activity?.run {
            var brightness = BRIGHTNESS_OVERRIDE_NONE
            if (!brightnessAuto() && value != BRIGHTNESS_OVERRIDE_NONE) {
                brightness = value
                if (brightness < 1f) brightness = 1f
                brightness /= 255f
            }
            val params = window.attributes
            params.screenBrightness = brightness
            window.attributes = params
        }
    }

    fun runMenuIn(anim: Boolean = !AppConfig.isEInkMode) {
        callBack.onMenuShow()
        this.visible()
        binding.titleBar.visible()
        binding.bottomMenu.visible()
        if (anim) {
            binding.titleBar.startAnimation(menuTopIn)
            binding.bottomMenu.startAnimation(menuBottomIn)
        } else {
            menuInListener.onAnimationStart(menuBottomIn)
            menuInListener.onAnimationEnd(menuBottomIn)
        }
    }

    fun runMenuOut(anim: Boolean = !AppConfig.isEInkMode, onMenuOutEnd: (() -> Unit)? = null) {
        if (isMenuOutAnimating) {
            return
        }
        callBack.onMenuHide()
        this.onMenuOutEnd = onMenuOutEnd
        if (this.isVisible) {
            if (anim) {
                binding.titleBar.startAnimation(menuTopOut)
                binding.bottomMenu.startAnimation(menuBottomOut)
            } else {
                menuOutListener.onAnimationStart(menuBottomOut)
                menuOutListener.onAnimationEnd(menuBottomOut)
            }
        }
    }

    private fun brightnessAuto(): Boolean {
        return context.getPrefBoolean("brightnessAuto", true) || !showBrightnessView
    }

    private fun bindEvent() = binding.run {
        vwMenuBg.setOnClickListener { runMenuOut() }
        titleBar.toolbar.setOnClickListener {
            callBack.openBookInfoActivity()
        }
        val chapterViewClickListener = OnClickListener {
            if (ReadBook.isLocalBook) {
                return@OnClickListener
            }
            if (AppConfig.readUrlInBrowser) {
                context.openUrl(tvChapterUrl.text.toString().substringBefore(",{"))
            } else {
                Coroutine.async {
                    context.startActivity<WebViewActivity> {
                        val url = tvChapterUrl.text.toString()
                        val bookSource = ReadBook.bookSource
                        putExtra("title", tvChapterName.text)
                        putExtra("url", url)
                        putExtra("sourceOrigin", bookSource?.bookSourceUrl)
                        putExtra("sourceName", bookSource?.bookSourceName)
                        putExtra("sourceType", bookSource?.getSourceType())
                    }
                }
            }
        }
        val chapterViewLongClickListener = OnLongClickListener {
            if (ReadBook.isLocalBook) {
                return@OnLongClickListener true
            }
            context.alert(R.string.open_fun) {
                setMessage(R.string.use_browser_open)
                okButton {
                    AppConfig.readUrlInBrowser = true
                }
                noButton {
                    AppConfig.readUrlInBrowser = false
                }
            }
            true
        }
        tvChapterName.setOnClickListener(chapterViewClickListener)
        tvChapterName.setOnLongClickListener(chapterViewLongClickListener)
        tvChapterUrl.setOnClickListener(chapterViewClickListener)
        tvChapterUrl.setOnLongClickListener(chapterViewLongClickListener)
        //自定义按钮
        tvCustomBtn.setOnClickListener {
            val book = ReadBook.book ?: return@setOnClickListener
            val chapter = ReadBook.curTextChapter?.chapter
            activity?.let { activity ->
                SourceCallBack.callBackBtn(
                    activity,
                    SourceCallBack.CLICK_CUSTOM_BUTTON,
                    ReadBook.bookSource,
                    book,
                    chapter
                )
            }
        }
        tvCustomBtn.setOnLongClickListener {
            val book = ReadBook.book ?: return@setOnLongClickListener true
            val chapter = ReadBook.curTextChapter?.chapter
            activity?.let { activity ->
                SourceCallBack.callBackBtn(
                    activity,
                    SourceCallBack.LONG_CLICK_CUSTOM_BUTTON,
                    ReadBook.bookSource,
                    book,
                    chapter
                )
            }
            true
        }
        //书源操作
        tvSourceAction.onClick {
            val items = buildList {
                if (ReadBook.bookSource?.let(::hasSourceLoginConfiguration) == true) {
                    add(SelectItem(context.getString(R.string.login), "login"))
                    if (ReadBook.curTextChapter?.isVip == true && ReadBook.curTextChapter?.isPay != true) {
                        add(SelectItem(context.getString(R.string.chapter_pay), "chapterPay"))
                    }
                }
                add(SelectItem(context.getString(R.string.edit_book_source), "editSource"))
                add(SelectItem(context.getString(R.string.disable_book_source), "disableSource"))
            }
            sourceActionMenu.setItems(items)
            sourceActionMenu.showAsDropDown(binding.tvSourceAction, 0, 4.dpToPx())
        }
        //亮度跟随
        ivBrightnessAuto.setOnClickListener {
            context.putPrefBoolean("brightnessAuto", !brightnessAuto())
            upBrightnessState()
        }
        //亮度调节
        seekBrightness.slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                setScreenBrightness(value)
            }
        }
        seekBrightness.slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {

            override fun onStartTrackingTouch(slider: Slider) = Unit

            override fun onStopTrackingTouch(slider: Slider) {
                AppConfig.readBrightness = slider.value.toInt()
            }

        })
        vwBrightnessPosAdjust.setOnClickListener {
            AppConfig.brightnessVwPos = !AppConfig.brightnessVwPos
            upBrightnessVwPos()
        }
        //阅读进度
        seekReadPage.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {

            override fun onStartTrackingTouch(slider: Slider) {
                binding.vwMenuBg.setOnClickListener(null)
            }

            override fun onStopTrackingTouch(slider: Slider) {
                binding.vwMenuBg.setOnClickListener { runMenuOut() }
                val progress = slider.value.toInt()
                when (AppConfig.progressBarBehavior) {
                    "page" -> ReadBook.skipToPage(progress)
                    "chapter" -> {
                        if (confirmSkipToChapter) {
                            callBack.skipToChapter(progress)
                        } else {
                            context.alert("章节跳转确认", "确定要跳转章节吗？") {
                                yesButton {
                                    confirmSkipToChapter = true
                                    callBack.skipToChapter(progress)
                                }
                                noButton {
                                    upSeekBar()
                                }
                                onCancelled {
                                    upSeekBar()
                                }
                            }
                        }
                    }
                }
            }

        })

        //搜索
        fabSearch.setOnClickListener {
            runMenuOut {
                callBack.openSearchActivity(null)
            }
        }

        //自动翻页
        fabAutoPage.setOnClickListener {
            runMenuOut {
                callBack.autoPage()
            }
        }

        //替换
        fabReplaceRule.setOnClickListener { callBack.openReplaceRule() }

        //夜间模式
        fabNightTheme.setOnClickListener {
            AppConfig.isNightTheme = !AppConfig.isNightTheme
            ThemeConfig.applyDayNight(context)
        }

        //上一章
        tvPre.setOnClickListener { ReadBook.moveToPrevChapter(upContent = true, toLast = false) }

        //下一章
        tvNext.setOnClickListener { ReadBook.moveToNextChapter(true) }

        //目录
        llCatalog.setOnClickListener {
            runMenuOut {
                callBack.openChapterList()
            }
        }

        //朗读
        llReadAloud.setOnClickListener {
            runMenuOut {
                callBack.onClickReadAloud()
            }
        }
        llReadAloud.onLongClick {
            runMenuOut {
                callBack.showReadAloudDialog()
            }
        }
        //界面
        llFont.setOnClickListener {
            runMenuOut {
                callBack.showReadStyle()
            }
        }

        //设置
        llSetting.setOnClickListener {
            runMenuOut {
                callBack.showMoreSetting()
            }
        }
    }

    private fun initAnimation() {
        menuTopIn.setAnimationListener(menuInListener)
        menuTopOut.setAnimationListener(menuOutListener)
    }

    fun upBookView() {
        binding.titleBar.title = ReadBook.book?.name
        ReadBook.curTextChapter?.let {
            binding.tvChapterName.text = it.title
            binding.tvChapterName.visible()
            if (!ReadBook.isLocalBook) {
                binding.tvChapterUrl.text = it.chapter.getAbsoluteURL()
                binding.tvChapterUrl.visible()
            } else {
                binding.tvChapterUrl.gone()
            }
            upSeekBar()
            binding.tvPre.isEnabled = ReadBook.durChapterIndex != 0
            binding.tvNext.isEnabled = ReadBook.durChapterIndex != ReadBook.simulatedChapterSize - 1
        } ?: let {
            binding.tvChapterName.gone()
            binding.tvChapterUrl.gone()
        }
    }

    fun upSeekBar() {
        binding.seekReadPage.apply {
            when (AppConfig.progressBarBehavior) {
                "page" -> {
                    ReadBook.curTextChapter?.let {
                        valueTo = it.pageSize.minus(1).coerceAtLeast(1).toFloat()
                        value = ReadBook.durPageIndex.toFloat().coerceIn(valueFrom, valueTo)
                    }
                }

                "chapter" -> {
                    valueTo = (ReadBook.simulatedChapterSize - 1).coerceAtLeast(1).toFloat()
                    value = ReadBook.durChapterIndex.toFloat().coerceIn(valueFrom, valueTo)
                }
            }
        }
    }

    fun setSeekPage(seek: Int) {
        binding.seekReadPage.value = seek.toFloat()
            .coerceIn(binding.seekReadPage.valueFrom, binding.seekReadPage.valueTo)
    }

    fun setAutoPage(autoPage: Boolean) = binding.run {
        if (autoPage) {
            fabAutoPage.setIconResource(R.drawable.ic_auto_page_stop)
            fabAutoPage.contentDescription = context.getString(R.string.auto_next_page_stop)
        } else {
            fabAutoPage.setIconResource(R.drawable.ic_auto_page)
            fabAutoPage.contentDescription = context.getString(R.string.auto_next_page)
        }
        fabAutoPage.setTint(textColor)
    }

    private fun upBrightnessVwPos() {
        if (AppConfig.brightnessVwPos) {
            binding.root.modifyBegin()
                .clear(R.id.ll_brightness, ConstraintModify.Anchor.LEFT)
                .rightToRightOf(R.id.ll_brightness, R.id.vw_menu_root)
                .commit()
        } else {
            binding.root.modifyBegin()
                .clear(R.id.ll_brightness, ConstraintModify.Anchor.RIGHT)
                .leftToLeftOf(R.id.ll_brightness, R.id.vw_menu_root)
                .commit()
        }
    }

    interface CallBack {
        fun autoPage()
        fun openReplaceRule()
        fun openChapterList()
        fun openSearchActivity(searchWord: String?)
        fun openSourceEditActivity()
        fun openBookInfoActivity()
        fun showReadStyle()
        fun showMoreSetting()
        fun showReadAloudDialog()
        fun upSystemUiVisibility()
        fun onClickReadAloud()
        fun showHelp()
        fun showLogin()
        fun payAction()
        fun disableSource()
        fun skipToChapter(index: Int)
        fun onMenuShow()
        fun onMenuHide()
    }

}
