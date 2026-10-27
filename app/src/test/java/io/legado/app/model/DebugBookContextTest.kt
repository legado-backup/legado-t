package io.legado.app.model

import io.legado.app.ProjectSources
import io.legado.app.constant.BookType
import io.legado.app.data.entities.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugBookContextTest {

    @Test
    fun directTocDebugProvidesBookAndSourceContext() {
        val source = BookSource(
            bookSourceUrl = "https://source.test",
            bookSourceName = "调试书源",
            bookSourceType = 1,
        )

        val book = Debug.createDebugBook(source, "https://book.test/toc/1")

        assertEquals("https://book.test/toc/1", book.bookUrl)
        assertEquals("https://book.test/toc/1", book.tocUrl)
        assertEquals("https://source.test", book.origin)
        assertEquals("调试书源", book.originName)
        assertEquals(BookType.audio, book.type)
    }

    @Test
    fun directContentDebugProvidesChapterBookAndBaseContext() {
        val source = BookSource(bookSourceUrl = "https://source.test", bookSourceType = 2)
        val url = "https://book.test/read/1"
        val book = Debug.createDebugBook(source, url)
        val chapter = Debug.createDebugChapter(book, url)

        assertEquals(url, chapter.url)
        assertEquals("调试", chapter.title)
        assertEquals(url, chapter.bookUrl)
        assertEquals(url, chapter.baseUrl)
        assertEquals(BookType.image, book.type)
    }

    @Test
    fun directTocAndContentRoutesUseDebugContextBeforeOtherUrlRoutes() {
        val debugSource = ProjectSources.app("src/main/java/io/legado/app/model/Debug.kt")
        val tocRoute = debugSource.indexOf("key.startsWith(\"++\")")
        val contentRoute = debugSource.indexOf("key.startsWith(\"--\")")
        val absoluteUrlRoute = debugSource.indexOf("key.isAbsUrl()")
        val exploreRoute = debugSource.indexOf("key.contains(\"::\")")

        assertTrue(tocRoute in 0 until absoluteUrlRoute)
        assertTrue(contentRoute in 0 until absoluteUrlRoute)
        assertTrue(tocRoute < exploreRoute)
        assertTrue(contentRoute < exploreRoute)
        assertTrue(debugSource.contains("val book = createDebugBook(bookSource, url)"))
        assertTrue(debugSource.contains("val chapter = createDebugChapter(book, url)"))
    }
}
