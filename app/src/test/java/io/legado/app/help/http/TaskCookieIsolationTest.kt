package io.legado.app.help.http

import io.legado.app.data.entities.SourceContract
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.toWebViewRequestHeaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.runBlocking

class TaskCookieIsolationTest {

    @Test
    fun disabledTaskContextDoesNotExposeScopeHeader() = runBlocking {
        kotlinx.coroutines.withContext(TaskCookieContext("task-disabled", false)) {
            assertNull(TaskCookieContext.currentHeaderValue())
        }
    }

    @Test
    fun enabledTaskContextUsesStableHashedScope() = runBlocking {
        kotlinx.coroutines.withContext(TaskCookieContext("task-1", true)) {
            assertEquals(
                CookieBridge.taskCookieScopeValue("task-1"),
                TaskCookieContext.currentHeaderValue()
            )
        }
    }

    @Test
    fun explicitCookieValuesOverrideStoredValues() {
        assertEquals(
            "session=explicit; stored=yes",
            CookieManager.mergeCookies(
                "session=stored; stored=yes",
                "session=explicit"
            )
        )
    }

    @Test
    fun internalTaskScopeHeaderIsRemovedBeforeWebViewRequest() {
        val headers = mapOf(
            "TaskCookieScope" to "task:secret",
            "CookieJar" to "1",
            "X-Test" to "ok"
        ).toWebViewRequestHeaders()
        assertEquals(mapOf("X-Test" to "ok"), headers)
    }

    @Test
    fun analyzeRuleUsesTaskCookieFacade() {
        val source = TestSource()
        val fake = FakeCookieStore("task-cookie")
        val oldStore = CookieBridge.store
        val oldFactory = CookieBridge.taskCookieStoreProvider
        try {
            CookieBridge.store = FakeCookieStore("global-cookie")
            CookieBridge.taskCookieStoreProvider = { _, _ -> fake }
            val result = AnalyzeRule(source = source).evalJS("cookie.getCookie('https://example.com')")
            assertEquals("task-cookie", result)
        } finally {
            CookieBridge.store = oldStore
            CookieBridge.taskCookieStoreProvider = oldFactory
        }
    }

    @Test
    fun taskCookieFacadeExposesGetKeyAndRemoveCookieToJavaScript() = runBlocking {
        val backend = RecordingTaskCookieBackend()
        val oldScopeProvider = CookieBridge.taskScopeProvider
        val oldFactory = CookieBridge.taskCookieStoreProvider
        try {
            CookieBridge.taskScopeProvider = TaskCookieContext::currentHeaderValue
            CookieBridge.taskCookieStoreProvider = { scope, sourceKey ->
                TaskCookieStore(scope, sourceKey, backend)
            }
            val result = kotlinx.coroutines.withContext(TaskCookieContext("task-1", true)) {
                AnalyzeRule().evalJS(
                    "var token = cookie.getKey('https://example.com', 'token'); " +
                        "cookie.removeCookie('https://example.com'); token"
                )
            }

            assertEquals("value", result)
            assertEquals(CookieBridge.taskCookieScopeValue("task-1"), backend.removedScope)
            assertEquals("https://example.com", backend.removedUrl)
        } finally {
            CookieBridge.taskScopeProvider = oldScopeProvider
            CookieBridge.taskCookieStoreProvider = oldFactory
        }
    }

    @Test
    fun blankCookieClearsOnlyCurrentTaskScope() {
        val backend = RecordingTaskCookieBackend()
        val store = TaskCookieStore("task-1", "source-1", backend)

        store.setCookie("https://example.com", null)
        assertEquals("task-1", backend.removedScope)
        assertEquals("https://example.com", backend.removedUrl)

        backend.removedUrl = null
        store.replaceCookie("https://example.com", "  ")
        assertEquals("https://example.com", backend.removedUrl)
    }

    @Test
    fun analyzeUrlUsesTaskCookieFacade() {
        val source = TestSource()
        val fake = FakeCookieStore("task-cookie")
        val oldStore = CookieBridge.store
        val oldFactory = CookieBridge.taskCookieStoreProvider
        try {
            CookieBridge.store = FakeCookieStore("global-cookie")
            CookieBridge.taskCookieStoreProvider = { _, _ -> fake }
            val analyzeUrl = AnalyzeUrl(
                mUrl = "https://example.com",
                baseUrl = "https://example.com",
                source = source
            )
            assertEquals("task-cookie", analyzeUrl.evalJS("cookie.getCookie(baseUrl)"))
        } finally {
            CookieBridge.store = oldStore
            CookieBridge.taskCookieStoreProvider = oldFactory
        }
    }

    private class TestSource : SourceContract {
        override val concurrentRate: String? = null
        override val enabledCookieJar: Boolean? = true
        override val cookieScopeKey: String? = "task-1"
        override val jsLib: String? = null

        override fun getKey(): String = "auto_task:task-1"
        override fun getTag(): String = "task"
        override fun getHeaderMap(hasLoginHeader: Boolean): Map<String, String> = emptyMap()
        override fun put(key: String, value: String): String = value
        override fun get(key: String): String = ""
        override fun getShareScope(coroutineContext: CoroutineContext?) = null
    }

    private class FakeCookieStore(private val value: String) :
        io.legado.app.help.http.api.CookieManagerInterface {
        override fun setCookie(url: String, cookie: String?) = Unit
        override fun replaceCookie(url: String, cookie: String) = Unit
        override fun getCookie(url: String): String = value
        override fun removeCookie(url: String) = Unit
        override fun cookieToMap(cookie: String) = mutableMapOf<String, String>()
        override fun mapToCookie(cookieMap: Map<String, String>?) = cookieMap?.toString()
    }

    private class RecordingTaskCookieBackend : TaskCookieBackend {
        var removedScope: String? = null
        var removedUrl: String? = null

        override fun setTaskCookie(scope: String, url: String, cookie: String?) = Unit
        override fun mergeTaskCookie(scope: String, url: String, cookie: String?) = Unit
        override fun getTaskCookie(scope: String, url: String): String = "token=value"
        override fun removeTaskCookie(scope: String, url: String) {
            removedScope = scope
            removedUrl = url
        }
    }
}
