package io.legado.app

import android.app.Application
import android.content.Intent
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.login.sourceCallbackRssTarget
import io.legado.app.ui.rss.read.ReadRssViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 使用真正的阅读 ViewModel 验证回调创建的最小文章能进入目标源加载链路，不访问网络。 */
@RunWith(AndroidJUnit4::class)
class RssCallbackLoadingTest {
    @Test
    fun missingArticleLoadsWithTargetSourceHeadersAndOriginalLink() {
        val origin = "https://${UUID.randomUUID()}.invalid"
        val link = "$origin/article"
        val source = RssSource(
            sourceUrl = origin, sourceName = "target RSS",
            header = """{"X-Target":"target-value"}""",
        )
        val target = sourceCallbackRssTarget(source, link, "callback title")
        val app = ApplicationProvider.getApplicationContext<Application>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val store = ViewModelStore()
        val loaded = CountDownLatch(1)
        var result: AnalyzeUrl? = null
        lateinit var model: ReadRssViewModel
        var modelCreated = false
        val observer = Observer<AnalyzeUrl> { result = it; loaded.countDown() }
        try {
            appDb.rssSourceDao.insert(source)
            assertNull(appDb.rssArticleDao.get(origin, link))
            // 与桥接相同的 IGNORE 操作，不覆盖文章/收藏的缓存内容。
            appDb.rssArticleDao.append(RssArticle(origin = target.origin, link = target.link!!, title = target.title))
            instrumentation.runOnMainSync {
                model = ReadRssViewModel(app)
                modelCreated = true
                store.put("rss-callback-test", model)
                model.urlLiveData.observeForever(observer)
                model.initData(Intent().apply { target.articleExtras().forEach { (key, value) -> putExtra(key, value) } })
            }
            assertTrue("最小文章未进入阅读页加载链路", loaded.await(20, TimeUnit.SECONDS))
            assertEquals(link, result?.url)
            assertEquals(origin, model.rssSource?.sourceUrl)
            assertEquals("callback title", model.rssArticle?.title)
            assertEquals("target-value", model.headerMap["X-Target"])
            assertEquals("target-value", result?.headerMap?.get("X-Target"))
        } finally {
            instrumentation.runOnMainSync {
                if (modelCreated) model.urlLiveData.removeObserver(observer)
                store.clear()
            }
            appDb.rssArticleDao.delete(origin)
            appDb.rssSourceDao.delete(origin)
        }
    }

    @Test
    fun callbackAppendDoesNotReplaceCachedArticleBodyOrVariables() {
        val origin = "https://${UUID.randomUUID()}.invalid"
        val article = RssArticle(origin = origin, link = "$origin/article", title = "cached", description = "saved body")
        try {
            appDb.rssArticleDao.insert(article)
            appDb.rssArticleDao.append(article.copy(title = "callback", description = null))
            assertEquals("cached", appDb.rssArticleDao.get(origin, article.link)?.title)
            assertEquals("saved body", appDb.rssArticleDao.get(origin, article.link)?.description)
        } finally {
            appDb.rssArticleDao.delete(origin)
        }
    }
}
