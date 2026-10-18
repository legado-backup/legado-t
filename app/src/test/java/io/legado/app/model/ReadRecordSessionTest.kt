package io.legado.app.model

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.ReadRecord
import org.junit.Assert.*
import org.junit.Test

class ReadRecordSessionTest {
    private var now = 0L
    private var enabled = true
    private val pending = mutableListOf<Pair<ReadRecord, Long>>()
    private val session = ReadRecordSession(
        deviceId = { "device" }, enabled = { enabled },
        save = { snapshot, elapsed -> pending += snapshot to elapsed },
        elapsedTime = { now }, wallTime = { 1000L + now },
    )

    @Test
    fun `switch settles old book before queue runs and freezes metadata`() {
        val old = Book(bookUrl = "old", name = "same", author = "first", coverUrl = "old-cover", origin = "old-source")
        session.setActive("page", true)
        session.setBook(old)
        now = 100
        session.setBook(Book(bookUrl = "new", name = "same", author = "second"))
        old.author = "changed after submission"
        now = 150
        session.checkpoint()

        assertEquals(listOf("old", "new"), pending.map { it.first.bookUrl })
        assertEquals(listOf(100L, 50L), pending.map { it.second })
        assertEquals("first", pending.first().first.author)
        assertEquals("old-cover", pending.first().first.coverUrl)
        assertEquals("old-source", pending.first().first.coverOrigin)
        assertTrue(pending.all { it.first.deviceId == "device" })
    }

    @Test
    fun `resume before initialization starts at initialization not singleton creation`() {
        now = 500
        session.setActive("page", true)
        now = 1000
        session.setBook(Book(bookUrl = "book"))
        now = 1100
        session.setActive("page", false)
        assertEquals(100L, pending.single().second)
        now = 9000
        session.checkpoint()
        assertEquals(1, pending.size)
    }

    @Test
    fun `pause excludes background time and repeated pause does not double count`() {
        session.setBook(Book(bookUrl = "book"))
        session.setActive("page", true)
        now = 100
        session.setActive("page", false)
        now = 5000
        session.setActive("page", false)
        session.checkpoint()
        session.setActive("page", true)
        now = 5075
        session.setActive("page", false)
        assertEquals(listOf(100L, 75L), pending.map { it.second })
    }

    @Test
    fun `page and read aloud overlap counts union not sum`() {
        session.setBook(Book(bookUrl = "book"))
        session.setActive("page", true)
        now = 100
        session.setActive("aloud", true)
        now = 200
        session.setActive("page", false)
        now = 400
        session.setActive("aloud", false)
        assertEquals(400L, pending.sumOf { it.second })
    }

    @Test
    fun `disabled to enabled starts at the toggle without an intermediate checkpoint`() {
        session.setEnabled(false)
        enabled = false
        session.setBook(Book(bookUrl = "book"))
        session.setActive("audio", true)
        now = 100
        // AppConfig 通知会话后才写入 preference；切换时没有翻页或额外 checkpoint。
        session.setEnabled(true)
        enabled = true
        now = 125
        session.setActive("audio", false)
        assertEquals(25L, pending.single().second)
    }

    @Test
    fun `enabled to disabled settles old interval and retains playback reason`() {
        session.setBook(Book(bookUrl = "book"))
        session.setActive("audio", true)
        now = 100
        session.setEnabled(false)
        enabled = false
        assertEquals(100L, pending.single().second)
        now = 5000
        session.setEnabled(true)
        enabled = true
        now = 5075
        // 不需要再次发 play；开关不能改变页面或音频的业务状态。
        session.setActive("audio", false)
        assertEquals(listOf(100L, 75L), pending.map { it.second })
    }

    @Test
    fun `disabled interval stays discarded when playback ends before enabling`() {
        session.setBook(Book(bookUrl = "book"))
        session.setActive("audio", true)
        now = 100
        session.setEnabled(false)
        now = 5000
        session.setActive("audio", false)
        session.setEnabled(true)
        now = 6000
        session.checkpoint()
        assertEquals(listOf(100L), pending.map { it.second })
    }

    @Test
    fun `terminal audio failure stops time until a later explicit play`() {
        session.setBook(Book(bookUrl = "book"))
        session.setActive("audio", true)
        now = 100
        // focus 拒绝和不可恢复播放错误均通过 AudioPlay.upReadTime 解除 audio。
        session.setActive("audio", false)
        now = 5000
        session.setActive("audio", false)
        session.checkpoint()
        assertEquals(listOf(100L), pending.map { it.second })
        session.setActive("audio", true)
        now = 5075
        session.setActive("audio", false)
        assertEquals(listOf(100L, 75L), pending.map { it.second })
    }

    @Test
    fun `replacement book supplies edited metadata without losing active time`() {
        val original = Book(bookUrl = "book", name = "old", author = "old author", coverUrl = "old-cover", origin = "source")
        session.setBook(original)
        session.setActive("page", true)
        now = 100
        val edited = original.copy(name = "edited", author = "new author", customCoverUrl = "custom")
        // 三个模型的 book setter 都执行同一套替换和立即保留快照操作。
        session.setBook(edited)
        session.refreshMetadata()
        assertEquals(0L, pending.last().second)
        assertEquals("new author", pending.last().first.author)
        assertEquals("custom", pending.last().first.coverUrl)
        assertEquals("", pending.last().first.coverOrigin)
        now = 150
        session.setActive("page", false)
        assertEquals(150L, pending.sumOf { it.second })
        assertEquals("edited", pending.last().first.bookName)
        assertEquals("new author", pending.last().first.author)
        original.author = "stale object changed"
        assertEquals("new author", pending.last().first.author)
    }

    @Test
    fun `clearing book settles old time and does not keep timing removed book`() {
        session.setBook(Book(bookUrl = "removed"))
        session.setActive("page", true)
        now = 100
        session.setBook(null)
        now = 5000
        session.checkpoint()
        session.refreshMetadata()
        session.setBook(Book(bookUrl = "next"))
        now = 5075
        session.setActive("page", false)
        assertEquals(listOf("removed", "next"), pending.map { it.first.bookUrl })
        assertEquals(listOf(100L, 75L), pending.map { it.second })
    }

    @Test
    fun `asynchronous details are persisted without page turn and custom cover has no origin`() {
        val book = Book(bookUrl = "book", origin = "source")
        session.setBook(book)
        book.author = "loaded author"
        book.coverUrl = "network"
        session.refreshMetadata()
        assertEquals("loaded author", pending.last().first.author)
        assertEquals("source", pending.last().first.coverOrigin)
        assertEquals(0L, pending.last().second)
        book.customCoverUrl = "local-cover"
        session.refreshMetadata()
        assertEquals("local-cover", pending.last().first.coverUrl)
        assertEquals("", pending.last().first.coverOrigin)
    }
}
