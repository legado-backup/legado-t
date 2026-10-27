package io.legado.app.ui.rss.source.edit

import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.entities.RssSource
import io.legado.app.databinding.ActivityRssSourceEditBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.tabTextColors
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.rss.source.debug.RssSourceDebugActivity
import io.legado.app.ui.widget.dialog.UrlOptionDialog
import io.legado.app.ui.widget.dialog.WebCodeDialog
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.ui.widget.keyboard.KeyboardToolPop
import io.legado.app.ui.widget.text.EditEntity
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.imeHeight
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isTrue
import io.legado.app.utils.launch
import io.legado.app.utils.navigationBarHeight
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.share
import io.legado.app.utils.shareWithQr
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import androidx.core.view.isGone
import androidx.core.view.isVisible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.bottomPadding

class RssSourceEditActivity :
    VMBaseActivity<ActivityRssSourceEditBinding, RssSourceEditViewModel>(),
    KeyboardToolPop.CallBack,
    VariableDialog.Callback,
    WebCodeDialog.Callback {

    override val binding by viewBinding(ActivityRssSourceEditBinding::inflate)
    override val viewModel by viewModels<RssSourceEditViewModel>()
    private val softKeyboardTool by lazy {
        KeyboardToolPop(this, lifecycleScope, binding.root, this)
    }
    private val webEditRequests = linkedMapOf<String, EditEntity>()
    private val adapter by lazy {
        RssSourceEditAdapter { entity ->
            // requestId 前缀编码 tab:key——Activity 重建丢 map 后仍可按其回填重建的同名字段
            val requestId = "${binding.tabLayout.selectedTabPosition}:${entity.key}:" +
                java.util.UUID.randomUUID().toString()
            if (
                WebCodeDialog.show(
                    supportFragmentManager,
                    entity.value.orEmpty(),
                    requestId = requestId,
                    title = entity.hint
                )
            ) {
                webEditRequests[requestId] = entity
            }
        }
    }
    private val sourceEntities: ArrayList<EditEntity> = ArrayList()
    private val listEntities: ArrayList<EditEntity> = ArrayList()
    private val webViewEntities: ArrayList<EditEntity> = ArrayList()
    private val selectDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            if (uri.isContentScheme()) {
                sendText(uri.toString())
            } else {
                sendText(uri.path.toString())
            }
        }
    }
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it?.let {
            viewModel.importSource(it) { source: RssSource ->
                upSourceView(source)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        softKeyboardTool.attachToWindow(window)
        initView()
        viewModel.initData(intent) {
            upSourceView(viewModel.rssSource)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (!LocalConfig.ruleHelpVersionIsLast) {
            showHelp("ruleHelp")
        }
    }

    override fun finish() {
        val source = getRssSource()
        if (!source.equal(viewModel.rssSource ?: RssSource())) {
            alert(R.string.exit) {
                setMessage(R.string.exit_no_save)
                positiveButton(R.string.yes)
                negativeButton(R.string.no) {
                    super.finish()
                }
            }
        } else {
            super.finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        softKeyboardTool.dismiss()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.source_edit, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_login)?.isVisible = !viewModel.rssSource?.loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_auto_complete)?.isChecked = viewModel.autoComplete
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> viewModel.save(getRssSource()) {
                setResult(RESULT_OK)
                finish()
            }

            R.id.menu_debug_source -> viewModel.save(getRssSource()) { source ->
                startActivity<RssSourceDebugActivity> {
                    putExtra("key", source.sourceUrl)
                }
            }

            R.id.menu_login -> viewModel.save(getRssSource()) {
                startActivity<SourceLoginActivity> {
                    putExtra("type", "rssSource")
                    putExtra("key", it.sourceUrl)
                }
            }

            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_clear_cookie -> viewModel.clearCookie(getRssSource().sourceUrl)
            R.id.menu_auto_complete -> viewModel.autoComplete = !viewModel.autoComplete
            R.id.menu_copy_source -> sendToClip(GSON.toJson(getRssSource()))
            R.id.menu_qr_code_camera -> qrCodeResult.launch()
            R.id.menu_paste_source -> viewModel.pasteSource { upSourceView(it) }
            R.id.menu_share_str -> share(GSON.toJson(getRssSource()))
            R.id.menu_share_qr -> shareWithQr(
                GSON.toJson(getRssSource()),
                getString(R.string.share_rss_source),
                ErrorCorrectionLevel.L
            )

            R.id.menu_help -> showHelp("ruleHelp")
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initView() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
            setText(R.string.source_tab_base)
        })
        binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
            setText(R.string.source_tab_list)
        })
        binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
            text = "WEB_VIEW"
        })
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.adapter = adapter
        setupTopHeader()
        // 沉浸式操作栏:tab 栏透明,与标题栏一致透出页面背景(含背景图);透明时去 elevation,避免不可见栏投影
        if (AppConfig.isTransparentActionBar) {
            binding.tabLayout.setBackgroundColor(Color.TRANSPARENT)
            binding.tabLayout.elevation = 0f
        } else {
            binding.tabLayout.setBackgroundColor(backgroundColor)
        }
        binding.tabLayout.setSelectedTabIndicatorColor(accentColor)
        val tabColors = tabTextColors(ColorUtils.isColorLight(backgroundColor))
        binding.tabLayout.setTabTextColors(tabColors.unselected, tabColors.selected)
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) {

            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {

            }

            override fun onTabSelected(tab: TabLayout.Tab?) {
                setEditEntities(tab?.position)
            }
        })
        binding.recyclerView.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
            val navigationBarHeight = windowInsets.navigationBarHeight
            val imeHeight = windowInsets.imeHeight
            view.bottomPadding = if (imeHeight == 0) navigationBarHeight else 0
            softKeyboardTool.initialPadding = imeHeight
            windowInsets
        }
        binding.fieldNav.attachToRecyclerView(binding.recyclerView)
    }

    private fun setupTopHeader() {
        binding.llTopHeader.setOnClickListener {
            val expand = binding.llTopExpand.isGone
            binding.llTopExpand.isVisible = expand
            binding.ivTopArrow.setImageResource(
                if (expand) R.drawable.ic_arrow_drop_up else R.drawable.ic_arrow_down
            )
            if (!expand) upTopSummary()
        }
    }

    private fun upTopSummary() {
        val parts = mutableListOf<String>()
        if (binding.cbIsEnable.isChecked) parts.add(getString(R.string.is_enable))
        if (binding.cbSingleUrl.isChecked) parts.add(getString(R.string.single_url))
        if (binding.cbIsEnableCookie.isChecked) parts.add(getString(R.string.auto_save_cookie))
        binding.tvTopSummary.text = parts.joinToString(" · ")
    }

    private fun setEditEntities(tabPosition: Int?) {
        val entities = when (tabPosition) {
            1 -> listEntities
            2 -> webViewEntities
            else -> sourceEntities
        }
        adapter.editEntities = entities
        binding.recyclerView.scrollToPosition(0)
        binding.fieldNav.setLabels(entities.map { it.hint.replace(Regex("[（(].+?[）)]"), "").trim() })
    }

    private fun upSourceView(rssSource: RssSource?) {
        val rs = rssSource ?: RssSource()
        rs.let {
            binding.cbIsEnable.isChecked = rs.enabled
            binding.cbSingleUrl.isChecked = rs.singleUrl
            binding.cbIsEnableCookie.isChecked = rs.enabledCookieJar == true
        }
        upTopSummary()
        sourceEntities.clear()
        sourceEntities.apply {
            add(EditEntity("sourceName", rs.sourceName, R.string.source_name))
            add(EditEntity("sourceUrl", rs.sourceUrl, R.string.source_url))
            add(EditEntity("sourceIcon", rs.sourceIcon, R.string.source_icon))
            add(EditEntity("sourceGroup", rs.sourceGroup, R.string.source_group))
            add(EditEntity("sourceComment", rs.sourceComment, R.string.comment))
            add(EditEntity("sortUrl", rs.sortUrl, R.string.sort_url))
            add(EditEntity("searchUrl", rs.searchUrl, R.string.rss_search_url))
            add(EditEntity("loginUrl", rs.loginUrl, R.string.login_url))
            add(EditEntity("loginUi", rs.loginUi, R.string.login_ui))
            add(EditEntity("loginCheckJs", rs.loginCheckJs, R.string.login_check_js))
            add(EditEntity("coverDecodeJs", rs.coverDecodeJs, R.string.cover_decode_js))
            add(EditEntity("header", rs.header, R.string.source_http_header))
            add(EditEntity("variableComment", rs.variableComment, R.string.variable_comment))
            add(EditEntity("concurrentRate", rs.concurrentRate, R.string.concurrent_rate))
            add(EditEntity("jsLib", rs.jsLib, "jsLib"))
        }
        listEntities.clear()
        listEntities.apply {
            add(EditEntity("ruleArticles", rs.ruleArticles, R.string.r_articles))
            add(EditEntity("ruleNextPage", rs.ruleNextPage, R.string.r_next))
            add(EditEntity("ruleTitle", rs.ruleTitle, R.string.r_title))
            add(EditEntity("rulePubDate", rs.rulePubDate, R.string.r_date))
            add(EditEntity("ruleDescription", rs.ruleDescription, R.string.r_description))
            add(EditEntity("ruleImage", rs.ruleImage, R.string.r_image))
            add(EditEntity("ruleLink", rs.ruleLink, R.string.r_link))
        }
        webViewEntities.clear()
        webViewEntities.apply {
            add(
                EditEntity(
                    "enableJs",
                    rs.enableJs.toString(),
                    R.string.enable_js,
                    EditEntity.ViewType.checkBox
                )
            )
            add(
                EditEntity(
                    "loadWithBaseUrl",
                    rs.loadWithBaseUrl.toString(),
                    R.string.load_with_base_url,
                    EditEntity.ViewType.checkBox
                )
            )
            add(EditEntity("ruleContent", rs.ruleContent, R.string.r_content))
            add(EditEntity("style", rs.style, R.string.r_style))
            add(EditEntity("injectJs", rs.injectJs, R.string.r_inject_js))
            add(EditEntity("contentWhitelist", rs.contentWhitelist, R.string.c_whitelist))
            add(EditEntity("contentBlacklist", rs.contentBlacklist, R.string.c_blacklist))
            add(
                EditEntity(
                    "shouldOverrideUrlLoading",
                    rs.shouldOverrideUrlLoading,
                    "url跳转拦截(js, 返回true拦截,js变量url,可以通过js打开url,比如调用阅读搜索,添加书架等,简化规则写法,不用webView js注入)"
                )
            )
        }
        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))
        setEditEntities(0)
    }

    private fun getRssSource(): RssSource {
        val source = viewModel.rssSource?.copy() ?: RssSource()
        source.enabled = binding.cbIsEnable.isChecked
        source.singleUrl = binding.cbSingleUrl.isChecked
        source.enabledCookieJar = binding.cbIsEnableCookie.isChecked
        sourceEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "sourceName" -> source.sourceName = it.value ?: ""
                "sourceUrl" -> source.sourceUrl = it.value ?: ""
                "sourceIcon" -> source.sourceIcon = it.value ?: ""
                "sourceGroup" -> source.sourceGroup = it.value
                "sourceComment" -> source.sourceComment = it.value
                "loginUrl" -> source.loginUrl = it.value
                "loginUi" -> source.loginUi = it.value
                "loginCheckJs" -> source.loginCheckJs = it.value
                "coverDecodeJs" -> source.coverDecodeJs = it.value
                "header" -> source.header = it.value
                "variableComment" -> source.variableComment = it.value
                "concurrentRate" -> source.concurrentRate = it.value
                "sortUrl" -> source.sortUrl = it.value
                "searchUrl" -> source.searchUrl = it.value
                "jsLib" -> source.jsLib = it.value
            }
        }
        listEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "ruleArticles" -> source.ruleArticles = it.value
                "ruleNextPage" -> source.ruleNextPage =
                    viewModel.ruleComplete(it.value, source.ruleArticles, 2)

                "ruleTitle" -> source.ruleTitle =
                    viewModel.ruleComplete(it.value, source.ruleArticles)

                "rulePubDate" -> source.rulePubDate =
                    viewModel.ruleComplete(it.value, source.ruleArticles)

                "ruleDescription" -> source.ruleDescription =
                    viewModel.ruleComplete(it.value, source.ruleArticles)

                "ruleImage" -> source.ruleImage =
                    viewModel.ruleComplete(it.value, source.ruleArticles, 3)

                "ruleLink" -> source.ruleLink =
                    viewModel.ruleComplete(it.value, source.ruleArticles)
            }
        }
        webViewEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "enableJs" -> source.enableJs = it.value.isTrue()
                "loadWithBaseUrl" -> source.loadWithBaseUrl = it.value.isTrue()
                "ruleContent" -> source.ruleContent =
                    viewModel.ruleComplete(it.value, source.ruleArticles)

                "style" -> source.style = it.value
                "injectJs" -> source.injectJs = it.value
                "contentWhitelist" -> source.contentWhitelist = it.value
                "contentBlacklist" -> source.contentBlacklist = it.value
                "shouldOverrideUrlLoading" -> source.shouldOverrideUrlLoading = it.value
            }
        }
        return source
    }

    private fun setSourceVariable() {
        viewModel.save(getRssSource()) { source ->
            lifecycleScope.launch {
                val comment =
                    source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
                val variable = withContext(Dispatchers.IO) { source.getVariable() }
                showDialogFragment(
                    VariableDialog(
                        getString(R.string.set_source_variable),
                        source.getKey(),
                        variable,
                        comment
                    )
                )
            }
        }
    }

    override fun setVariable(key: String, variable: String?) {
        viewModel.rssSource?.setVariable(variable)
    }

    override fun helpActions(): List<SelectItem<String>> {
        return arrayListOf(
            SelectItem("插入URL参数", "urlOption"),
            SelectItem("订阅源教程", "ruleHelp"),
            SelectItem("js教程", "jsHelp"),
            SelectItem("正则教程", "regexHelp"),
            SelectItem("选择文件", "selectFile"),
        )
    }

    override fun onHelpActionSelect(action: String) {
        when (action) {
            "urlOption" -> UrlOptionDialog(this) {
                sendText(it)
            }.show()

            "ruleHelp" -> showHelp("ruleHelp")
            "jsHelp" -> showHelp("jsHelp")
            "regexHelp" -> showHelp("regexHelp")
            "selectFile" -> selectDoc.launch {
                mode = HandleFileContract.FILE
            }
        }
    }

    override fun sendText(text: String) {
        if (text.isBlank()) return
        val view = window.decorView.findFocus()
        if (view is EditText) {
            val start = view.selectionStart
            val end = view.selectionEnd
            val edit = view.editableText//获取EditText的文字
            if (start < 0 || start >= edit.length) {
                edit.append(text)
            } else if (start > end) {
                edit.replace(end, start, text)
            } else {
                edit.replace(start, end, text)//光标所在位置插入文字
            }
        }
    }

    override fun onCodeSave(code: String, requestId: String?) {
        requestId ?: return
        val entity = webEditRequests.remove(requestId)
            ?: findEntityByRequestId(requestId)
            ?: return
        entity.value = code
        val index = adapter.editEntities.indexOf(entity)
        if (index >= 0) {
            adapter.notifyItemChanged(index)
        } else {
            adapter.notifyDataSetChanged()
        }
    }

    /** Activity 重建后 requestId→entity 映射丢失，按 requestId 内编码的 tab:key 定位重建后的同名字段 */
    private fun findEntityByRequestId(requestId: String): EditEntity? {
        val parts = requestId.split(":", limit = 3)
        if (parts.size < 3) return null
        val entities = when (parts[0].toIntOrNull() ?: return null) {
            1 -> listEntities
            2 -> webViewEntities
            else -> sourceEntities
        }
        return entities.find { it.key == parts[1] }
    }

}
