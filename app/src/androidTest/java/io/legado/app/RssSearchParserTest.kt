package io.legado.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.model.rss.RssParserDefault
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RssSearchParserTest {

    @Test
    fun defaultParserResolvesRelativeArticleLinksAgainstSearchResponseUrl() {
        val xml = """
            <rss><channel><item><title>Article</title><link>../article/1</link></item></channel></rss>
        """.trimIndent()

        val (articles, _) = RssParserDefault.parseXML(
            sortName = "search",
            xml = xml,
            sourceUrl = "https://rss.test/feed",
            baseUrl = "https://search.test/results/query"
        )

        assertEquals("https://search.test/article/1", articles.single().link)
    }
}
