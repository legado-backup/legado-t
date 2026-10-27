package io.legado.app.model.rss

import io.legado.app.data.entities.RssSource
import io.legado.app.model.analyzeRule.AnalyzeUrl
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import io.legado.app.help.http.HttpBridge
import org.junit.Assert.assertEquals
import org.junit.Test

class RssSearchUrlTest {

    @Test
    fun searchUrlUsesKeywordAndPageRules() = runBlocking {
        val previousClientFactory = HttpBridge.proxyClient
        var requestedUrl = ""
        try {
            HttpBridge.proxyClient = {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        requestedUrl = chain.request().url.toString()
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("ok".toResponseBody())
                            .build()
                    }
                    .build()
            }
            AnalyzeUrl(
                "https://rss.test/search?q={{key}}&page=<1,2,3>",
                key = "中文",
                page = 2,
                baseUrl = "https://rss.test"
            ).getStrResponseAwait(useWebView = false)

            assertEquals("https://rss.test/search?q=%E4%B8%AD%E6%96%87&page=2", requestedUrl)
        } finally {
            HttpBridge.proxyClient = previousClientFactory
        }
    }

    @Test
    fun equalTreatsSearchUrlAsSourceConfiguration() {
        val first = RssSource(sourceUrl = "https://rss.test", searchUrl = "https://rss.test/search")
        val same = first.copy()
        val changed = first.copy(searchUrl = "https://rss.test/find")

        assertEquals(true, first.equal(same))
        assertEquals(false, first.equal(changed))
    }
}
