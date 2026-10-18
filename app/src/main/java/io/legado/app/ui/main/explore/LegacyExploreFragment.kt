package io.legado.app.ui.main.explore

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.databinding.FragmentExploreLegacyBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.toolbarTextColor
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.activity
import io.legado.app.utils.applyTint
import io.legado.app.utils.cnCompare
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.startActivity
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 历史发现页:按书源展开分类,选择分类进入发现结果列表。 */
class LegacyExploreFragment() : VMBaseFragment<LegacyExploreViewModel>(R.layout.fragment_explore_legacy),
    MainFragmentInterface,
    LegacyExploreAdapter.CallBack {

    constructor(position: Int) : this() {
        arguments = Bundle().apply { putInt("position", position) }
    }

    override val position: Int? get() = arguments?.getInt("position")
    override val viewModel by viewModels<LegacyExploreViewModel>()
    private val binding by viewBinding(FragmentExploreLegacyBinding::bind)
    private val adapter by lazy { LegacyExploreAdapter(requireContext(), this) }
    private val searchView: SearchView get() = binding.titleBar.findViewById(R.id.search_view)
    private var exploreFlowJob: Job? = null
    private var groupMenu: MenuItem? = null
    private val groups = linkedSetOf<String>()
    private var expandedSourceUrl: String? = null
    private var expandedKindsJob: Job? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        initSearchView()
        initRecyclerView()
        initGroupData()
        upExploreData()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_explore_legacy, menu)
        groupMenu = menu.findItem(R.id.menu_group)
        upGroupsMenu()
    }

    override fun onPause() {
        super.onPause()
        searchView.clearFocus()
    }

    override fun onDestroyView() {
        exploreFlowJob?.cancel()
        expandedKindsJob?.cancel()
        expandedSourceUrl?.let { adapter.setLoading(it, false) }
        expandedSourceUrl = null
        adapter.setExpanded(null)
        binding.rvFind.adapter = null
        groupMenu = null
        super.onDestroyView()
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        if (item.groupId == R.id.menu_group_text) {
            searchView.setQuery("group:${item.title}", true)
        }
    }

    private fun initSearchView() {
        searchView.applyTint(toolbarTextColor)
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = getString(R.string.screen_find)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                upExploreData(newText)
                return false
            }
        })
    }

    private fun initRecyclerView() {
        binding.rvFind.setEdgeEffectColor(primaryColor)
        binding.rvFind.layoutManager = LinearLayoutManager(context)
        binding.rvFind.adapter = adapter
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                if (positionStart == 0) binding.rvFind.scrollToPosition(0)
            }
        })
    }

    private fun initGroupData() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookSourceDao.flowExplore().map { sources ->
                sources.flatMap {
                    it.bookSourceGroup.orEmpty().splitNotBlank(AppPattern.splitGroupRegex).toList()
                }.distinct().sortedWith { left, right -> left.cnCompare(right) }
            }.flowWithLifecycleAndDatabaseChange(
                    viewLifecycleOwner.lifecycle,
                    Lifecycle.State.RESUMED,
                    AppDatabase.BOOK_SOURCE_TABLE_NAME
                )
                .conflate()
                .distinctUntilChanged()
                .collect { loaded ->
                    groups.clear()
                    groups.addAll(loaded)
                    upGroupsMenu()
                }
        }
    }

    private fun upGroupsMenu() = groupMenu?.subMenu?.transaction { subMenu ->
        subMenu.removeGroup(R.id.menu_group_text)
        groups.forEach { group ->
            subMenu.add(R.id.menu_group_text, Menu.NONE, Menu.NONE, group)
        }
    }

    private fun upExploreData(searchKey: String? = null) {
        exploreFlowJob?.cancel()
        exploreFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            val flow = when {
                searchKey.isNullOrBlank() -> appDb.bookSourceDao.flowExplore()
                searchKey.startsWith("group:") -> {
                    val group = searchKey.substringAfter("group:")
                    appDb.bookSourceDao.flowExplore().map { sources ->
                        sources.filter { source ->
                            source.bookSourceGroup.orEmpty()
                                .splitNotBlank(AppPattern.splitGroupRegex)
                                .contains(group)
                        }
                    }
                }
                else -> appDb.bookSourceDao.flowExplore(searchKey)
            }
            flow.flowWithLifecycleAndDatabaseChange(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.BOOK_SOURCE_TABLE_NAME
            ).catch {
                AppLog.put("发现界面更新数据出错", it)
            }.conflate().flowOn(IO).collect { sources ->
                binding.tvEmptyMsg.isGone = sources.isNotEmpty() || searchView.query.isNotEmpty()
                val expanded = expandedSourceUrl
                if (expanded != null && sources.none { it.bookSourceUrl == expanded }) {
                    expandedSourceUrl = null
                    expandedKindsJob?.cancel()
                    adapter.setExpanded(null)
                    adapter.setLoading(expanded, false)
                    adapter.clearKinds(expanded)
                }
                adapter.setItems(sources, LegacyExploreAdapter.DIFF)
                delay(150)
            }
        }
    }

    override fun openExplore(sourceUrl: String, title: String, exploreUrl: String?) {
        if (exploreUrl.isNullOrBlank()) return
        startActivity<ExploreShowActivity> {
            putExtra("exploreName", title)
            putExtra("sourceUrl", sourceUrl)
            putExtra("exploreUrl", exploreUrl)
        }
    }

    override fun editSource(sourceUrl: String) {
        startActivity<BookSourceEditActivity> { putExtra("sourceUrl", sourceUrl) }
    }

    override fun toTop(source: BookSourcePart) = viewModel.topSource(source)

    override fun deleteSource(source: BookSourcePart) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + source.bookSourceName)
            noButton()
            yesButton { viewModel.deleteSource(source) }
        }
    }

    override fun searchBook(source: BookSourcePart) {
        startActivity<SearchActivity> {
            putExtra("searchScope", SearchScope(source).toString())
        }
    }

    override fun login(source: BookSourcePart) {
        startActivity<SourceLoginActivity> {
            putExtra("type", "bookSource")
            putExtra("key", source.bookSourceUrl)
        }
    }

    override fun refresh(source: BookSourcePart, position: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            source.clearExploreKindsCache()
            if (expandedSourceUrl == source.bookSourceUrl) {
                loadKinds(source, position)
            } else {
                adapter.clearKinds(source.bookSourceUrl)
            }
        }
    }

    override fun showError(title: String, message: String) {
        showDialogFragment(TextDialog(title, message))
    }

    override fun loadKinds(source: BookSourcePart, position: Int) {
        expandedKindsJob?.cancel()
        expandedSourceUrl = source.bookSourceUrl
        adapter.setLoading(source.bookSourceUrl, true)
        expandedKindsJob = viewLifecycleOwner.lifecycleScope.launch {
            val kinds: List<ExploreKind> = runCatching {
                // BookSourcePart 先查完整书源；不能在 Main 触发同步 Room 查询。
                withContext(IO) { source.exploreKinds() }
            }.getOrElse {
                if (it is CancellationException) throw it
                AppLog.put("发现分类加载失败", it)
                emptyList()
            }
            if (expandedSourceUrl == source.bookSourceUrl) {
                adapter.showKinds(source.bookSourceUrl, kinds)
                adapter.setLoading(source.bookSourceUrl, false)
            }
        }
    }

    override fun collapse(sourceUrl: String) {
        if (expandedSourceUrl == sourceUrl) {
            expandedSourceUrl = null
            expandedKindsJob?.cancel()
            adapter.clearKinds(sourceUrl)
            adapter.setLoading(sourceUrl, false)
        }
    }

    fun gotoTop() {
        if (AppConfig.isEInkMode) binding.rvFind.scrollToPosition(0)
        else binding.rvFind.smoothScrollToPosition(0)
    }
}
