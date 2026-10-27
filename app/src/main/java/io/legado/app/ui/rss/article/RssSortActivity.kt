package io.legado.app.ui.rss.article

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityRssArtivlesBinding
import io.legado.app.help.source.sortUrls
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.tabTextColors
import io.legado.app.lib.theme.toolbarTextColor
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 把 `java.open("sort", url, title, origin)` 的 url/title 组合成 RssSortActivity 的 sortUrl:
 * url 本身是 JSON 对象时原样使用(可同时打开多个分类), 否则用 title 包成 {"标题":"链接"}。
 */
internal fun rssSortUrl(url: String?, title: String?): String? = when {
    url.isJsonObject() -> url
    // 无链接时展示完整分类列表; 无标题时单分类标题留空
    url.isNullOrBlank() -> null
    title.isNullOrBlank() -> url
    else -> JSONObject().put(title, url).toString()
}

/** sortUrl 解析出的分类; 未指定时返回 null, 由调用方回退到 source.sortUrls()。非法 JSON 按普通链接处理。 */
internal fun parseSortTabs(sortUrl: String?): List<Pair<String, String>>? {
    val url = sortUrl?.takeIf { it.isNotBlank() } ?: return null
    if (!url.isJsonObject()) return listOf(Pair("", url))
    return GSONStrict.fromJsonObject<Map<String, String>>(url).getOrNull()
        ?.map { Pair(it.key, it.value) }
        ?: listOf(Pair("", url))
}

class RssSortActivity : VMBaseActivity<ActivityRssArtivlesBinding, RssSortViewModel>(),
    VariableDialog.Callback {

    override val binding by viewBinding(ActivityRssArtivlesBinding::inflate)
    override val viewModel by viewModels<RssSortViewModel>()
    private val adapter by lazy { TabFragmentPageAdapter(this) }
    private val sortList = mutableListOf<Pair<String, String>>()
    private var fragmentGeneration = 0L
    private var searchView: SearchView? = null
    private var searchMode = false
    private var searchKey = ""
    private val editSourceResult = registerForActivityResult(
        StartActivityContract(RssSourceEditActivity::class.java)
    ) {
        if (it.resultCode == RESULT_OK) {
            searchMode = false
            searchKey = ""
            searchView?.setQuery("", false)
            viewModel.initData(intent) {
                upFragments()
                invalidateOptionsMenu()
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.viewPager.adapter = adapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = sortList[position].first
        }.attach()
        binding.tabLayout.setSelectedTabIndicatorColor(accentColor)
        // tab 栏透明透出页面背景,文字明暗按页面背景判定
        val tabColors = tabTextColors(ColorUtils.isColorLight(backgroundColor))
        binding.tabLayout.setTabTextColors(tabColors.unselected, tabColors.selected)
        viewModel.titleLiveData.observe(this) {
            binding.titleBar.title = if (searchMode) {
                getString(R.string.rss_search_results, searchKey)
            } else {
                it
            }
        }
        val restoredSearchKey = savedInstanceState?.getString("rssSearchKey").orEmpty()
        viewModel.initData(intent) {
            if (restoredSearchKey.isNotBlank() && !viewModel.rssSource?.searchUrl.isNullOrBlank()) {
                startSearch(restoredSearchKey)
            } else {
                upFragments()
            }
            invalidateOptionsMenu()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (searchMode) outState.putString("rssSearchKey", searchKey)
        super.onSaveInstanceState(outState)
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.rss_articles, menu)
        menu.findItem(R.id.menu_search)?.isVisible =
            !viewModel.rssSource?.searchUrl.isNullOrBlank()
        searchView = (menu.findItem(R.id.menu_search).actionView as SearchView).apply {
            applyTint(toolbarTextColor)
            queryHint = getString(R.string.rss_search_hint)
            isSubmitButtonEnabled = true
            if (searchMode) {
                isIconified = false
                setQuery(searchKey, false)
            }
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    val key = query?.trim().orEmpty()
                    if (key.isNotEmpty()) startSearch(key)
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean = false
            })
            setOnCloseListener {
                clearSearch()
                false
            }
        }
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_login)?.isVisible =
            !viewModel.rssSource?.loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_search)?.isVisible =
            !viewModel.rssSource?.searchUrl.isNullOrBlank()
        return super.onMenuOpened(featureId, menu)
    }

    private fun startSearch(key: String) {
        if (viewModel.rssSource?.searchUrl.isNullOrBlank()) return
        searchMode = true
        searchKey = key
        searchView?.let {
            it.isIconified = false
            it.setQuery(key, false)
        }
        binding.tabLayout.gone()
        binding.titleBar.title = getString(R.string.rss_search_results, key)
        sortList.clear()
        sortList.add("" to (viewModel.rssSource?.searchUrl ?: ""))
        fragmentGeneration++
        adapter.notifyDataSetChanged()
    }

    private fun clearSearch() {
        if (!searchMode) return
        searchMode = false
        searchKey = ""
        searchView?.setQuery("", false)
        lifecycleScope.launch {
            val tabs = viewModel.sortTabs() ?: viewModel.rssSource?.sortUrls().orEmpty()
            sortList.clear()
            sortList.addAll(tabs)
            if (sortList.size == 1) binding.tabLayout.gone() else binding.tabLayout.visible()
            viewModel.rssSource?.sourceName?.let { binding.titleBar.title = it }
            fragmentGeneration++
            adapter.notifyDataSetChanged()
        }
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_login -> startActivity<SourceLoginActivity> {
                putExtra("type", "rssSource")
                putExtra("key", viewModel.rssSource?.sourceUrl)
            }

            R.id.menu_refresh_sort -> viewModel.clearSortCache { upFragments() }
            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_edit_source -> viewModel.rssSource?.sourceUrl?.let {
                editSourceResult.launch {
                    putExtra("sourceUrl", it)
                }
            }

            R.id.menu_clear -> {
                viewModel.url?.let {
                    viewModel.clearArticles()
                }
            }

            R.id.menu_switch_layout -> {
                viewModel.switchLayout()
                upFragments()
            }

            R.id.menu_read_record -> {
                showDialogFragment<ReadRecordDialog>()
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun upFragments() {
        lifecycleScope.launch {
            if (searchMode) {
                binding.tabLayout.gone()
                fragmentGeneration++
                adapter.notifyDataSetChanged()
                return@launch
            }
            // 深链指定的分类优先, 未指定时才展示订阅源的完整分类列表
            val tabs = viewModel.sortTabs() ?: viewModel.rssSource?.sortUrls()
            tabs?.let {
                sortList.clear()
                sortList.addAll(it)
            }
            if (sortList.size == 1) {
                binding.tabLayout.gone()
            } else {
                binding.tabLayout.visible()
            }
            // 原 getItemPosition 恒 POSITION_NONE:每次刷新全量重建(内容/布局风格可能变);
            // 递增 generation 使所有 itemId 变化,等价全量重建
            fragmentGeneration++
            adapter.notifyDataSetChanged()
        }
    }

    private fun setSourceVariable() {
        lifecycleScope.launch {
            val source = viewModel.rssSource
            if (source == null) {
                toastOnUi("源不存在")
                return@launch
            }
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

    override fun setVariable(key: String, variable: String?) {
        viewModel.rssSource?.setVariable(variable)
    }

    private inner class TabFragmentPageAdapter(activity: FragmentActivity) :
        FragmentStateAdapter(activity) {

        override fun getItemCount(): Int {
            return sortList.size
        }

        override fun getItemId(position: Int): Long {
            // 高位=generation,低位=position:刷新后 generation 变→所有 id 变→全量重建,等价原 POSITION_NONE
            return (fragmentGeneration shl 20) or position.toLong()
        }

        override fun containsItem(itemId: Long): Boolean {
            return (itemId ushr 20) == fragmentGeneration && (itemId and 0xFFFFF) < sortList.size
        }

        override fun createFragment(position: Int): Fragment {
            val sort = sortList[position]
            return if (searchMode) {
                RssArticlesFragment(sort.first, sort.second, searchKey)
            } else {
                RssArticlesFragment(sort.first, sort.second)
            }
        }
    }

    companion object {
        /**
         * @param sortUrl 指定分类链接, 为空则展示订阅源的完整分类列表;
         *                可传 {"标题":"链接"} 形式的 JSON 同时打开多个分类
         * @param sourceUrl 目标订阅源 sourceUrl, 用于复用其请求头/Cookie 与解析规则
         */
        fun start(context: Context, sortUrl: String?, sourceUrl: String) {
            context.startActivity<RssSortActivity> {
                putExtra("sortUrl", sortUrl)
                putExtra("url", sourceUrl)
            }
        }
    }

}