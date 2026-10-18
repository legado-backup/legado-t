package io.legado.app.ui.login

import androidx.lifecycle.Lifecycle
import com.google.gson.JsonParser
import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.model.login.LoginUiV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class SourceLoginBridgeTest {

    /**
     * 使用真实 BaseSource 的 eval/登录装配，仅替换 shared scope 和持久化。
     * 非空 shared scope 避免裸 JVM 测试触发平台提供的 CryptoJS 资源加载。
     */
    class SourceProbe : BaseSource {
        override var concurrentRate: String? = null
        override var loginUrl: String? = null
        override var loginUi: String? = null
        override var header: String? = null
        override var enabledCookieJar: Boolean? = false
        override var jsLib: String? = null
        private val values = hashMapOf<String, String>()

        override fun getTag() = "login-test"
        override fun getKey() = "https://login.test"
        override fun getShareScope(coroutineContext: CoroutineContext?): ScriptBindings =
            RhinoScriptEngine.getRuntimeScope(ScriptBindings())

        override fun put(key: String, value: String): String = value.also { values[key] = it }
        override fun get(key: String): String = values[key].orEmpty()
    }

    @Test
    fun loginEntryRequiresUrlOrBothJsScriptAndUi() {
        assertTrue(hasSourceLoginConfiguration(BookSource(loginUrl = "https://login.test")))
        assertFalse(hasSourceLoginConfiguration(BookSource(mainJs = "function search() {}")))
        assertFalse(hasSourceLoginConfiguration(BookSource(loginUi = "[]")))
        assertTrue(
            hasSourceLoginConfiguration(
                BookSource(mainJs = "function login() {}", loginUi = "[]")
            )
        )
        assertFalse(hasSourceLoginConfiguration(BookSource(loginUrl = " \n", mainJs = " ", loginUi = "[]")))
        assertFalse(hasSourceLoginConfiguration(BookSource(mainJs = "function login() {}", loginUi = " \n")))
    }

    @Test
    fun rssArticleRoutePassesTargetOriginTitleAndUnmodifiedLink() {
        val source = RssSource(sourceUrl = "https://rss.test", sourceName = "订阅")
        val url = "https://rss.test/article,{'headers':{'Referer':'https://rss.test'}}"
        val target = sourceCallbackRssTarget(source, url, "book name")
        assertEquals(SourceCallbackRssPage.ARTICLE, target.page)
        assertEquals(
            mapOf("title" to "book name", "origin" to "https://rss.test", "link" to url),
            target.articleExtras(),
        )
        assertEquals("订阅", sourceCallbackRssTarget(source, url, null).title)
    }

    @Test
    fun rssEmptyLinkUsesExistingListSinglePageAndExternalRoutes() {
        val source = RssSource(sourceUrl = "https://rss.test", sourceName = "订阅")
        assertEquals(SourceCallbackRssPage.SORT, sourceCallbackRssTarget(source, " \n", null).page)
        source.singleUrl = true
        val single = sourceCallbackRssTarget(source, null, null)
        assertEquals(SourceCallbackRssPage.ARTICLE, single.page)
        assertFalse(single.articleExtras().containsKey("link"))
        source.sourceUrl = "custom://rss"
        assertEquals(SourceCallbackRssPage.EXTERNAL, sourceCallbackRssTarget(source, null, null).page)
    }

    @Test
    fun onlyAnActiveUnsavedOriginalActivityCanHandleUi() {
        Lifecycle.State.values().forEach { state ->
            assertEquals(
                state == Lifecycle.State.STARTED || state == Lifecycle.State.RESUMED,
                canUseSourceCallbackUi(state, isFinishing = false, isDestroyed = false, isStateSaved = false),
            )
        }
        assertFalse(canUseSourceCallbackUi(Lifecycle.State.RESUMED, true, false, false))
        assertFalse(canUseSourceCallbackUi(Lifecycle.State.RESUMED, false, true, false))
        assertFalse(canUseSourceCallbackUi(Lifecycle.State.RESUMED, false, false, true))
    }

    @Test
    fun facadeRetainsSourceAndHasNoUiFallbackWithoutItsActivity() {
        val source = SourceProbe()
        val java = SourceCallbackJsExtensions(null, source)
        assertSame(source, java.getSource())
        // 无原页面时须直接忽略，不能触达 Android Main dispatcher/其他 Activity。
        java.searchBook("book")
        java.addBook("https://book.test")
        java.open("login")
        java.open("search", title = "book")
        java.open("explore", "https://explore.test")
    }

    @Test
    fun sourceEvalKeepsItsOriginalJavaBindingForNotificationCallbacks() {
        val source = SourceProbe()
        assertEquals(true, source.evalJS("java === source"))
        assertEquals("login-test", source.evalJS("java.getSource().getTag()"))
    }

    @Test
    fun v1RenderBindsPageJavaWithoutReplacingSourceOrBookContext() {
        val source = SourceProbe().apply {
            loginUi = """@js:[{name: String(java === source), type: 'button', action: typeof java.open}]"""
        }
        val book = Book(name = "test book")
        val chapter = BookChapter(title = "test chapter")
        val rows = source.loginUiWithJava(book, chapter, SourceCallbackJsExtensions(null, source))!!
        assertEquals("false", rows.single().name)
        assertEquals("function", rows.single().action)

        source.loginUi = """@js:[{name: String(book.name) + ':' + String(chapter.title), type:'text'}]"""
        assertEquals("test book:test chapter", source.loginUiWithJava(book, chapter).singleRowName())

        source.loginUi = """@js:[{name: String(java === source), type:'text'}]"""
        assertEquals("true", source.loginUi(book, chapter).singleRowName())
    }

    @Test
    fun v1ActionAndFinalLoginSeeThePageJavaFacade() {
        val source = SourceProbe()
        val java = SourceCallbackJsExtensions(null, source)
        val actionResult = source.evalJS("String(result.account) + ':' + typeof java.searchBook + ':' + (java === source)") {
            put("result", mapOf("account" to "reader"))
            put("java", java)
        }
        assertEquals("reader:function:false", actionResult)

        source.loginUrl = """function login() { source.put('binding', String(java === source) + ':' + typeof java.open); }"""
        source.loginWithJava(java = java)
        assertEquals("false:function", source.get("binding"))
    }

    @Test
    fun noArgAndOriginalTwoArgLoginRemainCallableFromRhino() {
        val source = SourceProbe().apply {
            loginUrl = """function login() { source.put('binding', String(java === source)); }"""
        }
        val bindings = ScriptBindings().apply { put("source", source) }
        RhinoScriptEngine.eval("source.login()", bindings)
        assertEquals("true", source.get("binding"))
        source.put("binding", "reset")
        RhinoScriptEngine.eval("source.login(null, null)", bindings)
        assertEquals("true", source.get("binding"))

        assertNotNull(BaseSource::class.java.getMethod("login"))
        assertNotNull(BaseSource::class.java.getMethod("login", Book::class.java, BookChapter::class.java))
    }

    @Test
    fun v2PageEntryBindsJavaAndOldEntryKeepsDefaultBinding() {
        val source = v2Source()
        val java = SourceCallbackJsExtensions(null, source)
        val pageRows = LoginUiV2.parseRender(source.evalLoginUiV2WithJava("{}", java = java))!!
        assertEquals("function", pageRows.single().name)
        val oldRows = LoginUiV2.parseRender(source.evalLoginUiV2("{}"))!!
        assertEquals("source", oldRows.single().name)

        val pageCmd = source.evalLoginActionV2WithJava("submit", "{}", """{"value":"reader"}""", java = java)
        assertEquals("function", actionState(pageCmd, "binding"))
        assertEquals("reader", actionState(pageCmd, "value"))
        val oldCmd = source.evalLoginActionV2("submit", "{}", "{}")
        assertEquals("source", actionState(oldCmd, "binding"))
    }

    @Test
    fun v2OriginalJvmSignaturesRemainCallableFromRhino() {
        val source = v2Source()
        val bindings = ScriptBindings().apply { put("source", source) }
        val render = RhinoScriptEngine.eval("source.evalLoginUiV2('{}', null, null)", bindings) as String
        assertEquals("source", LoginUiV2.parseRender(render)!!.single().name)
        val action = RhinoScriptEngine.eval(
            "source.evalLoginActionV2('submit', '{}', '{}', null, null)", bindings,
        ) as String
        assertEquals("source", actionState(action, "binding"))

        assertNotNull(
            BaseSource::class.java.getMethod(
                "evalLoginUiV2", String::class.java, Book::class.java, BookChapter::class.java,
            )
        )
        assertNotNull(
            BaseSource::class.java.getMethod(
                "evalLoginActionV2", String::class.java, String::class.java, String::class.java,
                Book::class.java, BookChapter::class.java,
            )
        )
    }

    private fun v2Source() = SourceProbe().apply {
        loginUi = """{"version":2}"""
        loginUrl = """
            function binding() { return java === source ? 'source' : typeof java.open; }
            function loginUi(state) { return {rows:[{name:binding(), type:'label'}]}; }
            function loginAction(action, state, form) {
                return {state:{binding:binding(), value:form.value || ''}};
            }
        """.trimIndent()
    }

    private fun actionState(json: String?, key: String): String =
        JsonParser.parseString(json).asJsonObject.getAsJsonObject("state").get(key).asString

    private fun List<io.legado.app.data.entities.rule.RowUi>?.singleRowName(): String = this!!.single().name
}
