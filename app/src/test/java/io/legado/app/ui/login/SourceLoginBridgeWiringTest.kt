package io.legado.app.ui.login

import io.legado.app.ProjectSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 裸 JVM 无 Activity/Fragment 调度环境，额外锁定实际页面使用被引擎测试覆盖的入口。 */
class SourceLoginBridgeWiringTest {

    @Test
    fun callbackUiNeverBorrowsAnotherActivityAndRechecksAfterIo() {
        val code = ProjectSources.app("src/main/java/io/legado/app/ui/login/SourceCallbackJsExtensions.kt")
        assertFalse(code.contains("LifecycleHelp"))
        assertFalse(code.contains("getTopActivity"))
        assertTrue(code.contains("activity.lifecycleScope.launch(Main.immediate + uiErrorHandler)"))
        assertTrue(code.contains("if (!activity.canUseUi()) return"))
    }

    @Test
    fun rssRouteCreatesOnlyMissingArticlesAndUsesTheTestedExtras() {
        val code = ProjectSources.app("src/main/java/io/legado/app/ui/login/SourceCallbackJsExtensions.kt")
        assertTrue(code.contains("\"rss\" -> openRss(activity, url, title, origin)"))
        assertTrue(code.contains("appDb.rssSourceDao.getByKey(origin)"))
        assertTrue(code.contains("appDb.rssArticleDao.append(RssArticle("))
        assertFalse(code.contains("appDb.rssArticleDao.insert("))
        assertTrue(code.contains("sourceCallbackRssTarget(toSource, url, title)"))
        assertTrue(code.contains("target.articleExtras().forEach"))
        assertTrue(code.contains("activity.startActivity<ReadRssActivity>"))
    }

    @Test
    fun loginPagesUseViewScopedJobsAndDedicatedJavaEntries() {
        val dialog = ProjectSources.app("src/main/java/io/legado/app/ui/login/SourceLoginDialog.kt")
        assertTrue(dialog.contains("viewLifecycleOwner.lifecycleScope.launch"))
        assertFalse(dialog.contains("lifecycleScope.launch(IO)"))
        assertTrue(dialog.contains("source.loginUiWithJava(book, chapter, java)"))
        assertTrue(dialog.contains("source.loginWithJava(book, chapter, java)"))
        assertTrue(dialog.contains("val loginData = getLoginData(loginUi)"))

        val v2 = ProjectSources.app("src/main/java/io/legado/app/ui/login/SourceLoginV2Delegate.kt")
        assertTrue(v2.contains("fragment.viewLifecycleOwner.lifecycleScope"))
        assertTrue(v2.contains("source.evalLoginUiV2WithJava("))
        assertTrue(v2.contains("source.evalLoginActionV2WithJava("))
        assertTrue(v2.contains("if (!destroyed)"))
    }

    @Test
    fun loginV2CapturesSessionInputAfterAsyncRenderCompletes() {
        val v2 = ProjectSources.app("src/main/java/io/legado/app/ui/login/SourceLoginV2Delegate.kt")
        val render = v2.substringAfter("    private fun render() {")
            .substringBefore("    private fun showRenderError() {")

        val renderIo = render.indexOf("withContext(IO)")
        val captureInput = render.indexOf("val sessionInput = collectForm()")
        val rebuildViews = render.indexOf("buildViews(rows, sessionInput, result.second)")

        assertTrue("render must start before capturing live form values", renderIo >= 0)
        assertTrue("form values must be captured after async render", captureInput > renderIo)
        assertTrue("captured values must be applied to the rebuilt views", rebuildViews > captureInput)
    }

    @Test
    fun notificationCallbacksKeepTheOriginalSourceJavaBinding() {
        val callback = ProjectSources.app("src/main/java/io/legado/app/model/SourceCallBack.kt")
        val notifications = callback.substringAfter("    fun callBackBook(")
        assertFalse(notifications.contains("SourceCallbackJsExtensions("))
        assertFalse(notifications.contains("put(\"java\""))
        assertFalse(notifications.contains("remove(\"java\""))
    }

}
