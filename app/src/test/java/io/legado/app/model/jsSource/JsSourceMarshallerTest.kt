package io.legado.app.model.jsSource

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsSourceMarshallerTest {

    private val textSource = BookSource(
        bookSourceUrl = "https://src.test", bookSourceName = "文本源",
        bookSourceType = 0, customOrder = 7,
    )
    private val audioSource = BookSource(
        bookSourceUrl = "https://audio.test", bookSourceName = "音频源",
        bookSourceType = 1,
    )

    @Test
    fun validateBookTypeAcceptsOnlyKnownBits() {
        assertEquals(BookType.text, JsSourceMarshaller.validateBookType(BookType.text))
        assertEquals(BookType.audio, JsSourceMarshaller.validateBookType(BookType.audio))
        assertEquals(
            BookType.text or BookType.webFile,
            JsSourceMarshaller.validateBookType(BookType.text or BookType.webFile),
        )
        assertNull(JsSourceMarshaller.validateBookType(0))
        assertNull(JsSourceMarshaller.validateBookType(1))    // BookSourceType.audio 误传形态,必须拒
        assertNull(JsSourceMarshaller.validateBookType(BookType.updateError)) // 非源可设位
    }

    @Test
    fun parseSearchBooksInjectsOriginAndType() {
        val json = """[
          {"name":"书A","author":"作者A","bookUrl":"https://src.test/b/1",
           "origin":"evil","originName":"evil","type":1},
          {"name":"书B","bookUrl":"https://src.test/b/2"}
        ]"""
        val books = JsSourceMarshaller.parseSearchBooks(json, audioSource)
        assertEquals(2, books.size)
        // origin 族强制注入,JS 返回的 evil 被覆盖
        assertEquals("https://audio.test", books[0].origin)
        assertEquals("音频源", books[0].originName)
        // type=1 是 BookSourceType 误传 → 拒收,回退源类型(audio=32)
        assertEquals(BookType.audio, books[0].type)
        // 未返回 type → 源类型
        assertEquals(BookType.audio, books[1].type)
    }

    @Test
    fun parseSearchBooksHonorsExplicitValidType() {
        val json = """[{"name":"书A","bookUrl":"u1","type":${BookType.text}}]"""
        val books = JsSourceMarshaller.parseSearchBooks(json, audioSource)
        assertEquals(BookType.text, books[0].type)   // 合法位值显式覆写生效(spec §3)
    }

    @Test
    fun parseSearchBooksDropsItemsMissingRequiredFields() {
        val json = """[
          {"name":"无url"},
          {"bookUrl":"无name"},
          {"name":"好","bookUrl":"u"}
        ]"""
        val books = JsSourceMarshaller.parseSearchBooks(json, textSource)
        assertEquals(1, books.size)
        assertEquals("好", books[0].name)
    }

    @Test
    fun parseSearchBooksLazyDelegateSurvivesGson() {
        val json = """[{"name":"书","bookUrl":"u","variable":"{\"k\":\"v\"}"}]"""
        val books = JsSourceMarshaller.parseSearchBooks(json, textSource)
        // 锁 lazy 委托在 GSON 实例化路径下可用
        assertEquals("v", books[0].variableMap["k"])
    }

    @Test
    fun mergeBookInfoOnlyTouchesPresentKeys() {
        val book = Book(bookUrl = "u", name = "原名", author = "原作者")
        book.intro = "原简介"
        JsSourceMarshaller.mergeBookInfo(book, """{"intro":"新简介","tocUrl":"t"}""", textSource)
        assertEquals("新简介", book.intro)
        assertEquals("t", book.tocUrl)
        assertEquals("原名", book.name)       // 未出现的 key 不动
        assertEquals("原作者", book.author)
    }

    @Test
    fun mergeBookInfoIgnoresPrimaryKeyAndUserState() {
        val book = Book(bookUrl = "keep")
        book.durChapterIndex = 5
        JsSourceMarshaller.mergeBookInfo(
            book,
            """{"bookUrl":"hack","durChapterIndex":0,"customTag":"hack"}""",
            textSource,
        )
        assertEquals("keep", book.bookUrl)
        assertEquals(5, book.durChapterIndex)
        assertNull(book.customTag)
    }

    @Test
    fun parseChaptersInjectsAndResolves() {
        val book = Book(bookUrl = "https://src.test/b/1")
        book.tocUrl = "https://src.test/toc/1"
        val json = """[
          {"title":"第1章","url":"/read/1"},
          {"title":"第2章","url":"https://other.test/read/2","isVip":true},
          {"url":"无标题"}
        ]"""
        val chapters = JsSourceMarshaller.parseChapters(json, book, textSource)
        assertEquals(2, chapters.size)
        assertEquals("https://src.test/read/1", chapters[0].url)     // 相对 → 绝对
        assertEquals("https://src.test/b/1", chapters[0].bookUrl)    // 注入
        assertEquals("https://src.test/toc/1", chapters[0].baseUrl)  // 注入
        assertEquals(0, chapters[0].index)
        assertEquals(1, chapters[1].index)                          // 按数组序注入
        assertTrue(chapters[1].isVip)                               // 可选字段透传
    }

    @Test
    fun parseChaptersVolumeRowKeepsUrlAsTitle() {
        val book = Book(bookUrl = "https://src.test/b/1")
        book.tocUrl = "https://src.test/toc/1"
        val json = """[
          {"title":"第一卷","url":"第一卷","isVolume":true},
          {"title":"第1章","url":"/read/1"}
        ]"""
        val chapters = JsSourceMarshaller.parseChapters(json, book, textSource)
        assertEquals(2, chapters.size)
        // 卷行 url==title 豁免绝对化——JsSourceBook 卷占位守卫(url.startsWith(title))因此可达
        assertEquals("第一卷", chapters[0].url)
        assertTrue(chapters[0].isVolume)
        assertEquals("https://src.test/read/1", chapters[1].url)
    }

    @Test
    fun mergeBookInfoVariableSyncsMaterializedMap() {
        val book = Book(bookUrl = "u")
        book.variable = """{"old":"1"}"""
        assertEquals("1", book.variableMap["old"])   // 先物化 lazy map
        JsSourceMarshaller.mergeBookInfo(book, """{"variable":"{\"k\":\"v\"}"}""", textSource)
        assertEquals("v", book.variableMap["k"])      // 物化后的 map 也被同步
        assertNull(book.variableMap["old"])           // 整体替换语义,旧键不残留
        assertTrue(book.variable!!.contains("\"k\""))
    }

    @Test
    fun mergeBookInfoDownloadUrlsLandsAndResolves() {
        val book = Book(bookUrl = "https://src.test/b/1")
        JsSourceMarshaller.mergeBookInfo(
            book,
            """{"downloadUrls":["/down/1.txt","https://cdn.test/2.epub"]}""",
            textSource,
        )
        // 文件源数据出口:相对 → 按详情页补全,绝对原样
        assertEquals(
            listOf("https://src.test/down/1.txt", "https://cdn.test/2.epub"),
            book.downloadUrls,
        )
    }

    @Test
    fun mergeBookInfoDownloadUrlsNonArrayIgnoredNotCrash() {
        val book = Book(bookUrl = "u")
        JsSourceMarshaller.mergeBookInfo(book, """{"downloadUrls":"not-array"}""", textSource)
        assertNull(book.downloadUrls)
        JsSourceMarshaller.mergeBookInfo(book, """{"downloadUrls":[{"bad":1}]}""", textSource)
        assertNull(book.downloadUrls)
    }

    @Test
    fun mergeBookInfoNonNumericTypeIsIgnoredNotCrash() {
        val book = Book(bookUrl = "u")
        val before = book.type
        JsSourceMarshaller.mergeBookInfo(book, """{"type":"audio"}""", textSource)
        assertEquals(before, book.type)   // 忽略且不崩;log 行为无法在裸JVM断言,靠实现走 debugLog 壳
    }
}
