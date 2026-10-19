package io.legado.app.ui.login

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.help.JsExtensionsAndroid
import io.legado.app.ui.association.AddToBookshelfDialog
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.rss.article.RssSortActivity
import io.legado.app.ui.rss.read.ReadRssActivity
import io.legado.app.utils.openUrl
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/** 与 book_sources_part 的登录入口判断一致；mainJs 本身不代表源提供登录 UI。 */
internal fun hasSourceLoginConfiguration(source: BaseSource): Boolean {
    return !source.loginUrl.isNullOrBlank() ||
        (source is BookSource && !source.mainJs.isNullOrBlank() && !source.loginUi.isNullOrBlank())
}

internal enum class SourceCallbackRssPage { ARTICLE, SORT, EXTERNAL }

internal data class SourceCallbackRssTarget(
    val page: SourceCallbackRssPage,
    val title: String,
    val origin: String,
    val link: String?,
) {
    fun articleExtras(): Map<String, String> = buildMap {
        put("title", title)
        put("origin", origin)
        link?.let { put("link", it) }
    }
}

/** 与订阅页保持相同入口语义；保留原始 link 以支持订阅源的 AnalyzeUrl 规则。 */
internal fun sourceCallbackRssTarget(source: RssSource, url: String?, title: String?): SourceCallbackRssTarget {
    val link = url?.takeUnless { it.isBlank() }
    val page = when {
        link != null -> SourceCallbackRssPage.ARTICLE
        !source.singleUrl -> SourceCallbackRssPage.SORT
        source.sourceUrl.startsWith("http", true) -> SourceCallbackRssPage.ARTICLE
        else -> SourceCallbackRssPage.EXTERNAL
    }
    return SourceCallbackRssTarget(page, title ?: source.sourceName, source.sourceUrl, link)
}

/** 只允许仍可交互的原页面发起导航/弹窗，不借用其他前台 Activity。 */
internal fun canUseSourceCallbackUi(
    state: Lifecycle.State,
    isFinishing: Boolean,
    isDestroyed: Boolean,
    isStateSaved: Boolean,
): Boolean {
    return state.isAtLeast(Lifecycle.State.STARTED) &&
        !isFinishing && !isDestroyed && !isStateSaved
}

@Suppress("unused")
class SourceCallbackJsExtensions(
    activity: AppCompatActivity?,
    private val source: BaseSource?,
) : JsExtensionsAndroid {

    private val activityRef = WeakReference(activity)
    private val uiErrorHandler = CoroutineExceptionHandler { _, error ->
        AppLog.put("源页面操作失败", error)
    }

    // 脚本及其异步 open 任务必须强持有源；Activity 则不应被 facade 延长生命周期。
    override fun getSource(): BaseSource? = source

    /** Login UI V2 的 java facade 也保留源变量存取能力。 */
    fun put(key: String, value: String): String = source?.put(key, value) ?: value

    fun get(key: String): String = source?.get(key).orEmpty()

    private fun AppCompatActivity.canUseUi(): Boolean = canUseSourceCallbackUi(
        lifecycle.currentState,
        isFinishing,
        isDestroyed,
        supportFragmentManager.isStateSaved,
    )

    fun searchBook(key: String) {
        runOnUiActivity { SearchActivity.start(it, key) }
    }

    fun addBook(bookUrl: String) {
        runOnUiActivity { it.showDialogFragment(AddToBookshelfDialog(bookUrl)) }
    }

    private fun runOnUiActivity(action: (AppCompatActivity) -> Unit) {
        val activity = activityRef.get() ?: return
        activity.lifecycleScope.launch(Main.immediate + uiErrorHandler) {
            if (activity.canUseUi()) action(activity)
        }
    }

    /**
     * 页面跳转调度
     * @param name login=源登录页 rss=订阅阅读页 search=书籍搜索 explore=发现结果页
     * @param url rss=文章链接(空则开列表/单页源) explore=发现地址
     * @param title 页面标题; search 时为搜索词
     * @param origin 指定目标源(书源url/订阅源url), 缺省当前源
     */
    @JvmOverloads
    fun open(name: String, url: String? = null, title: String? = null, origin: String? = null) {
        runOnUiActivity { activity ->
            activity.lifecycleScope.launch(Main.immediate + uiErrorHandler) {
                when (name) {
                    "login" -> openLogin(activity, origin)
                    "rss" -> openRss(activity, url, title, origin)
                    "search" -> title?.let { SearchActivity.start(activity, it) }
                    "explore" -> {
                        if (url.isNullOrBlank()) return@launch
                        val toSource = withContext(IO) {
                            if (origin.isNullOrBlank()) {
                                source as? BookSource
                            } else {
                                appDb.bookSourceDao.getBookSource(origin)
                            }
                        } ?: return@launch
                        // 数据库读取期间原页面可能已退出、停止或保存了 Fragment 状态。
                        if (!activity.canUseUi()) return@launch
                        activity.startActivity<ExploreShowActivity> {
                            putExtra("exploreName", title)
                            putExtra("sourceUrl", toSource.bookSourceUrl)
                            putExtra("exploreUrl", url)
                        }
                    }
                }
            }
        }
    }

    private suspend fun openRss(activity: AppCompatActivity, url: String?, title: String?, origin: String?) {
        val target = withContext(IO) {
            val toSource = if (origin.isNullOrBlank()) {
                source as? RssSource
            } else {
                appDb.rssSourceDao.getByKey(origin)
            } ?: return@withContext null
            sourceCallbackRssTarget(toSource, url, title).also { target ->
                target.link?.let { link ->
                    // ReadRssViewModel 对明确 link 只读取已有文章；IGNORE 不覆盖缓存正文/用户变量。
                    appDb.rssArticleDao.append(RssArticle(origin = target.origin, link = link, title = target.title))
                }
            }
        } ?: return
        if (!activity.canUseUi()) return
        when (target.page) {
            SourceCallbackRssPage.ARTICLE -> activity.startActivity<ReadRssActivity> {
                // origin 使阅读页重载目标订阅源，沿用其 headers/Cookie/正文规则，而非当前书源。
                target.articleExtras().forEach { (key, value) -> putExtra(key, value) }
            }
            SourceCallbackRssPage.SORT -> activity.startActivity<RssSortActivity> {
                putExtra("url", target.origin)
            }
            SourceCallbackRssPage.EXTERNAL -> activity.openUrl(target.origin)
        }
    }

    /** 在 Main 执行导航，只有查找目标源离开主线程。 */
    private suspend fun openLogin(activity: AppCompatActivity, origin: String?) {
        val toSource = withContext(IO) {
            if (origin.isNullOrBlank()) {
                source
            } else {
                appDb.bookSourceDao.getBookSource(origin) ?: appDb.rssSourceDao.getByKey(origin)
            }
        }
        if (!activity.canUseUi()) return
        if (toSource == null) {
            activity.toastOnUi("未找到源")
            return
        }
        if (!hasSourceLoginConfiguration(toSource)) {
            activity.toastOnUi("源未配置登录")
            return
        }
        val (type, key) = when (toSource) {
            is BookSource -> "bookSource" to toSource.bookSourceUrl
            is RssSource -> "rssSource" to toSource.sourceUrl
            else -> return
        }
        activity.startActivity<SourceLoginActivity> {
            putExtra("type", type)
            putExtra("key", key)
        }
    }
}
