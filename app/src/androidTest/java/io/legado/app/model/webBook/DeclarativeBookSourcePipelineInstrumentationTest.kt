package io.legado.app.model.webBook

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.data.entities.rule.TocRule
import io.legado.app.help.http.HttpBridge
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import fi.iki.elonen.NanoHTTPD

@RunWith(AndroidJUnit4::class)
class DeclarativeBookSourcePipelineInstrumentationTest {

    private lateinit var server: FixtureServer
    private lateinit var baseUrl: String
    private lateinit var source: BookSource
    private lateinit var previousHttpClient: (String?) -> OkHttpClient
    private lateinit var previousSearchBookFactory: (String?) -> io.legado.app.data.entities.SearchBookContract
    private lateinit var previousBookFactory: (io.legado.app.data.entities.Book) -> io.legado.app.data.entities.SearchBookContract

    @Before
    fun setUp() {
        server = FixtureServer()
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        baseUrl = "http://127.0.0.1:${server.listeningPort}/"
        previousHttpClient = HttpBridge.proxyClient
        previousSearchBookFactory = WebBookBridge.newSearchBook
        previousBookFactory = WebBookBridge.bookToSearchBook
        HttpBridge.proxyClient = { OkHttpClient() }
        source = BookSource(
            bookSourceUrl = baseUrl,
            bookSourceName = "fixture",
            searchUrl = "${baseUrl}search?q={{key}}",
            ruleSearch = SearchRule(
                bookList = ".book",
                name = ".name@text",
                author = ".author@text",
                bookUrl = ".name@href"
            ),
            ruleBookInfo = BookInfoRule(
                name = ".title@text",
                author = ".author@text",
                intro = ".intro@text",
                coverUrl = ".cover@src",
                tocUrl = ".toc@href"
            ),
            ruleToc = TocRule(
                chapterList = ".chapter",
                chapterName = "a@text",
                chapterUrl = "a@href"
            ),
            ruleContent = ContentRule(content = ".content@html")
        )
        WebBookBridge.newSearchBook = { variable -> SearchBook(variable = variable) }
        WebBookBridge.bookToSearchBook = { book ->
            SearchBook(
                bookUrl = book.bookUrl,
                origin = book.origin,
                originName = book.originName,
                name = book.name,
                author = book.author,
                intro = book.intro,
                coverUrl = book.coverUrl,
                tocUrl = book.tocUrl,
                latestChapterTitle = book.latestChapterTitle,
                wordCount = book.wordCount,
                variable = book.variable
            )
        }
    }

    @After
    fun tearDown() {
        server.stop()
        HttpBridge.proxyClient = previousHttpClient
        WebBookBridge.newSearchBook = previousSearchBookFactory
        WebBookBridge.bookToSearchBook = previousBookFactory
    }

    @Test
    fun loginCheckJsCanRefetchAuthenticatedSearchResponse() = runBlocking {
        source.loginCheckJs = "java.connect(baseUrl + 'auth-search')"

        val results = WebBook.searchBookAwait(source, "one", 1)

        assertEquals(1, results.size)
        assertEquals("One Book", results.single().name)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun searchInfoTocAndContentFollowRelativeLinks() = runBlocking {
        val results = WebBook.searchBookAwait(source, "one", 1)
        assertEquals(1, results.size)
        val searchBook = results.single() as SearchBook
        assertEquals("One Book", searchBook.name)
        assertEquals("Author One", searchBook.author)
        assertEquals("${baseUrl}books/one", searchBook.bookUrl)

        val book = searchBook.toBook()
        WebBook.getBookInfoAwait(source, book)
        assertEquals("A fixture intro", book.intro)
        assertEquals("${baseUrl}covers/one.png", book.coverUrl)
        assertEquals("${baseUrl}toc/one", book.tocUrl)

        val chapters = WebBook.getChapterListAwait(source, book).getOrThrow()
        assertEquals(listOf("Chapter One", "Chapter Two"), chapters.map { it.title })
        assertEquals("${baseUrl}chapter/one", chapters.first().url)

        val content = WebBook.getContentAwait(source, book, chapters.first(), needSave = false)
        assertTrue(content.contains("Hello"))
        assertTrue(content.contains("reader"))
        assertEquals(4, server.requestCount)
    }

    private class FixtureServer : NanoHTTPD(0) {
        var requestCount = 0
            private set

        override fun serve(session: IHTTPSession): Response {
            requestCount++
            val body = when (session.uri) {
                "/search", "/auth-search" -> """
                    <html><body>
                      <div class="book"><a class="name" href="books/one">One Book</a><span class="author">Author One</span></div>
                    </body></html>
                """.trimIndent()
                "/books/one" -> """
                    <html><body>
                      <h1 class="title">One Book</h1><span class="author">Author One</span>
                      <p class="intro">A fixture intro</p><img class="cover" src="/covers/one.png">
                      <a class="toc" href="toc/one">TOC</a>
                    </body></html>
                """.trimIndent()
                "/toc/one" -> """
                    <html><body>
                      <div class="chapter"><a href="../chapter/one">Chapter One</a></div>
                      <div class="chapter"><a href="../chapter/two">Chapter Two</a></div>
                    </body></html>
                """.trimIndent()
                "/chapter/one" -> "<article class=\"content\"><p>Hello <b>reader</b>.</p></article>"
                else -> "not found"
            }
            val status = if (session.uri in setOf("/search", "/auth-search", "/books/one", "/toc/one", "/chapter/one")) {
                Response.Status.OK
            } else {
                Response.Status.NOT_FOUND
            }
            return newFixedLengthResponse(status, "text/html; charset=utf-8", body)
        }
    }
}
