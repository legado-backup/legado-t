package io.legado.app.model.jsSource

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsSourceReviewTest {

    @Test
    fun parseSummaryNormal() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "https://a.test",
            bookSourceName = "段评测试",
            mainJs = """
                var config = { bookSourceUrl: "https://a.test", bookSourceName: "test" }
                function search(k,p) { return [] }
                function getChapters(b) { return [] }
                function getContent(c,b) { return "" }
                function getReviewSummary(chapter, book) {
                    return [
                        { paraIndex: 1, count: 5, paraData: "token-a" },
                        { paraIndex: 3, count: 2 }
                    ]
                }
                function getReviewDetail(c,b,pi,pd,pg) { return {items:[]} }
            """.trimIndent()
        )
        val book = Book(bookUrl = "https://a.test/book/1", name = "测试书")
        val chapter = BookChapter(bookUrl = book.bookUrl, title = "第1章", url = "https://a.test/c1")

        val result = JsSourceReview.getReviewSummaryAwait(source, book, chapter)

        assertNotNull(result)
        assertEquals(2, result!!.size)
        assertEquals(5, result[1]!!.first)
        assertEquals("token-a", result[1]!!.second)
        assertEquals(2, result[3]!!.first)
        assertEquals("3", result[3]!!.second)  // paraData 缺失时默认 paraIndex.toString()
    }

    @Test
    fun parseSummaryIgnoreZeroCount() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "https://a.test",
            mainJs = """
                var config = { bookSourceUrl: "https://a.test", bookSourceName: "test" }
                function search(k,p) { return [] }
                function getChapters(b) { return [] }
                function getContent(c,b) { return "" }
                function getReviewSummary(c,b) {
                    return [
                        { paraIndex: 1, count: 5 },
                        { paraIndex: 2, count: 0 },
                        { paraIndex: 3, count: -1 }
                    ]
                }
                function getReviewDetail(c,b,pi,pd,pg) { return {items:[]} }
            """.trimIndent()
        )
        val book = Book(bookUrl = "https://a.test/book/1", name = "测试书")
        val chapter = BookChapter(bookUrl = book.bookUrl, title = "第1章", url = "https://a.test/c1")

        val result = JsSourceReview.getReviewSummaryAwait(source, book, chapter)

        assertNotNull(result)
        assertEquals(1, result!!.size)  // 只有 paraIndex=1 的 count>0
        assertEquals(5, result[1]!!.first)
    }

    @Test
    fun parseSummaryEmptyArray() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "https://a.test",
            mainJs = """
                var config = { bookSourceUrl: "https://a.test", bookSourceName: "test" }
                function search(k,p) { return [] }
                function getChapters(b) { return [] }
                function getContent(c,b) { return "" }
                function getReviewSummary(c,b) { return [] }
                function getReviewDetail(c,b,pi,pd,pg) { return {items:[]} }
            """.trimIndent()
        )
        val book = Book(bookUrl = "https://a.test/book/1", name = "测试书")
        val chapter = BookChapter(bookUrl = book.bookUrl, title = "第1章", url = "https://a.test/c1")

        val result = JsSourceReview.getReviewSummaryAwait(source, book, chapter)

        assertNotNull(result)
        assertTrue(result!!.isEmpty())
    }

    @Test
    fun parseDetailNormal() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "https://a.test",
            mainJs = """
                var config = { bookSourceUrl: "https://a.test", bookSourceName: "test" }
                function search(k,p) { return [] }
                function getChapters(b) { return [] }
                function getContent(c,b) { return "" }
                function getReviewSummary(c,b) { return [] }
                function getReviewDetail(chapter, book, paraIndex, paraData, page) {
                    return {
                        items: [
                            { id: "123", content: "评论内容", name: "用户A", avatar: "https://a.test/av.jpg", badge: "作者" }
                        ],
                        nextPageUrl: page < 2 ? "https://a.test/page2" : null
                    }
                }
            """.trimIndent()
        )
        val book = Book(bookUrl = "https://a.test/book/1", name = "测试书")
        val chapter = BookChapter(bookUrl = book.bookUrl, title = "第1章", url = "https://a.test/c1")

        val result = JsSourceReview.getReviewDetailAwait(source, book, chapter, 1, "data-x", 1)

        assertNotNull(result)
        val (items, nextUrl) = result!!
        assertEquals(1, items.size)
        assertEquals("123", items[0].id)
        assertEquals("评论内容", items[0].content)
        assertEquals("用户A", items[0].name)
        assertEquals("https://a.test/av.jpg", items[0].avatar)
        assertEquals("作者", items[0].badge)
        assertTrue(items[0].replies.isEmpty())
        assertEquals("https://a.test/page2", nextUrl)
    }

    @Test
    fun parseDetailNestedReplies() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "https://a.test",
            mainJs = """
                var config = { bookSourceUrl: "https://a.test", bookSourceName: "test" }
                function search(k,p) { return [] }
                function getChapters(b) { return [] }
                function getContent(c,b) { return "" }
                function getReviewSummary(c,b) { return [] }
                function getReviewDetail(c,b,pi,pd,pg) {
                    return {
                        items: [{
                            content: "主评论",
                            replies: [
                                { content: "回复1", id: "r1" },
                                { content: "回复2", name: "B", replies: [{ content: "嵌套回复" }] }
                            ]
                        }],
                        nextPageUrl: null
                    }
                }
            """.trimIndent()
        )
        val book = Book(bookUrl = "https://a.test/book/1", name = "测试书")
        val chapter = BookChapter(bookUrl = book.bookUrl, title = "第1章", url = "https://a.test/c1")

        val result = JsSourceReview.getReviewDetailAwait(source, book, chapter, 1, "", 1)

        assertNotNull(result)
        val items = result!!.first
        assertEquals(1, items.size)
        assertEquals("主评论", items[0].content)
        assertEquals(2, items[0].replies.size)
        assertEquals("回复1", items[0].replies[0].content)
        assertEquals("r1", items[0].replies[0].id)
        assertEquals("回复2", items[0].replies[1].content)
        assertEquals("B", items[0].replies[1].name)
        assertEquals(1, items[0].replies[1].replies.size)
        assertEquals("嵌套回复", items[0].replies[1].replies[0].content)
        assertNull(result.second)
    }

    @Test
    fun parseDetailMissingContentDropped() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "https://a.test",
            mainJs = """
                var config = { bookSourceUrl: "https://a.test", bookSourceName: "test" }
                function search(k,p) { return [] }
                function getChapters(b) { return [] }
                function getContent(c,b) { return "" }
                function getReviewSummary(c,b) { return [] }
                function getReviewDetail(c,b,pi,pd,pg) {
                    return {
                        items: [
                            { content: "正常评论" },
                            { id: "bad", name: "无content" },
                            { content: "" }
                        ]
                    }
                }
            """.trimIndent()
        )
        val book = Book(bookUrl = "https://a.test/book/1", name = "测试书")
        val chapter = BookChapter(bookUrl = book.bookUrl, title = "第1章", url = "https://a.test/c1")

        val result = JsSourceReview.getReviewDetailAwait(source, book, chapter, 1, "", 1)

        assertNotNull(result)
        assertEquals(1, result!!.first.size)
        assertEquals("正常评论", result.first[0].content)
    }

    @Test
    fun parseDetailRepliesNullDoesNotCrash() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "https://a.test",
            mainJs = """
                var config = { bookSourceUrl: "https://a.test", bookSourceName: "test" }
                function search(k,p) { return [] }
                function getChapters(b) { return [] }
                function getContent(c,b) { return "" }
                function getReviewSummary(c,b) { return [] }
                function getReviewDetail(c,b,pi,pd,pg) {
                    return {
                        items: [
                            { content: "评论", replies: null },
                            { content: "无replies字段" }
                        ],
                        nextPageUrl: null
                    }
                }
            """.trimIndent()
        )
        val book = Book(bookUrl = "https://a.test/book/1", name = "测试书")
        val chapter = BookChapter(bookUrl = book.bookUrl, title = "第1章", url = "https://a.test/c1")

        val result = JsSourceReview.getReviewDetailAwait(source, book, chapter, 1, "", 1)

        assertNotNull(result)
        assertEquals(2, result!!.first.size)
        assertTrue(result.first[0].replies.isEmpty())
        assertTrue(result.first[1].replies.isEmpty())
    }
}
