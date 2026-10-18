package io.legado.app.model.jsSource

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.ContentEmptyException
import io.legado.app.exception.TocEmptyException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JsSourceBookInstrumentationTest {

    @Test
    fun javaBridgeStringOverloadsRemainCallableFromRealRhinoSource() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "https://example.test",
            bookSourceName = "fixture",
            mainJs = """
                function search(key, page) {
                    var bytes = java.strToBytes(key, 'UTF-8')
                    return [{name: java.bytesToStr(bytes, 'UTF-8'), bookUrl: baseUrl + '/' + page}]
                }
            """.trimIndent()
        )

        val books = JsSourceBook.searchAwait(source, "桥接", 3)
        assertEquals(1, books.size)
        assertEquals("桥接", books[0].name)
        assertEquals("https://example.test/3", books[0].bookUrl)
    }

    @Test
    fun realSearchInfoTocAndContentEntryPointsExecute() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "https://example.test",
            bookSourceName = "fixture",
            mainJs = """
                function search(key, page) {
                    return [{name: key, author: 'Author ' + page, bookUrl: baseUrl + '/book/' + page}]
                }
                function getBookInfo(book) {
                    return {intro: 'Fixture intro', tocUrl: book.bookUrl + '/toc'}
                }
                function getChapters(book) {
                    return [
                        {title: 'Chapter One', url: book.tocUrl + '/one'},
                        {title: 'Chapter Two', url: book.tocUrl + '/two'}
                    ]
                }
                function getContent(chapter, book) {
                    return 'Content for ' + chapter.title
                }
            """.trimIndent()
        )

        val books = JsSourceBook.searchAwait(source, "needle", 2)
        assertEquals(1, books.size)
        assertEquals("needle", books[0].name)
        assertEquals("Author 2", books[0].author)
        assertEquals("https://example.test/book/2", books[0].bookUrl)
        assertEquals(source.bookSourceUrl, books[0].origin)

        val book = books[0].toBook()
        JsSourceBook.getBookInfoAwait(source, book)
        assertEquals("Fixture intro", book.intro)
        assertEquals("https://example.test/book/2/toc", book.tocUrl)

        val chapters = JsSourceBook.getChapterListAwait(source, book).getOrThrow()
        assertEquals(listOf("Chapter One", "Chapter Two"), chapters.map { it.title })
        assertEquals("https://example.test/book/2/toc/one", chapters[0].url)
        assertEquals(2, book.totalChapterNum)

        val content = JsSourceBook.getContentAwait(source, book, chapters[0], needSave = false)
        assertEquals("Content for Chapter One", content)
    }

    @Test
    fun invalidSearchAndEmptyContentAreReportedAtPublicBoundary() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "https://example.test",
            bookSourceName = "fixture",
            mainJs = """
                function search() { return [{author: 'missing name', bookUrl: '/ignored'}] }
                function getChapters() { return [] }
                function getContent() { return '   ' }
            """.trimIndent()
        )

        assertTrue(JsSourceBook.searchAwait(source, "needle", 1).isEmpty())

        val book = Book(bookUrl = "https://example.test/book", tocUrl = "https://example.test/toc")
        val chapters = JsSourceBook.getChapterListAwait(source, book)
        assertTrue(chapters.exceptionOrNull() is TocEmptyException)

        val chapter = BookChapter(
            title = "Normal",
            url = "https://example.test/chapter",
            bookUrl = book.bookUrl,
            baseUrl = book.tocUrl
        )
        try {
            JsSourceBook.getContentAwait(source, book, chapter, needSave = false)
            throw AssertionError("blank JS content must fail")
        } catch (error: ContentEmptyException) {
            assertEquals("内容为空", error.message)
        }
    }

    @Test
    fun volumeChapterSkipsJsContent() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "https://example.test",
            bookSourceName = "fixture",
            mainJs = "function getContent() { throw 'must not run' }"
        )
        val book = Book(bookUrl = "https://example.test/book")
        val chapter = BookChapter(
            title = "Volume One",
            url = "Volume One0",
            isVolume = true,
            bookUrl = book.bookUrl
        )

        assertEquals("", JsSourceBook.getContentAwait(source, book, chapter, needSave = false))
    }
}
