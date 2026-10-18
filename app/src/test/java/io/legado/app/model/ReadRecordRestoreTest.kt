package io.legado.app.model

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.ReadRecord
import io.legado.app.help.storage.ReadRecordRestore
import io.legado.app.utils.GSON
import org.junit.Assert.*
import org.junit.Test

class ReadRecordRestoreTest {
    @Test
    fun `old backup reuses migrated legacy key after book is added again`() {
        val old = GSON.fromJson("""{"deviceId":"","bookName":"name","readTime":100,"lastRead":1}""", ReadRecord::class.java)
        val migrated = old.copy(bookUrl = ReadRecord.legacyBookUrl("name"))
        val newBook = Book(bookUrl = "new", name = "name", author = "author")
        val restored = ReadRecordRestore.resolve(old, listOf(migrated), listOf(newBook))
        assertEquals(migrated.bookUrl, restored.bookUrl)
        assertEquals(100L, migrated.merge(restored).readTime)
    }

    @Test
    fun `old backup reuses previous resolved url after source change`() {
        val old = ReadRecord(deviceId = "device", bookName = "name", readTime = 100)
        val existing = old.copy(bookUrl = "old-source")
        val restored = ReadRecordRestore.resolve(old, listOf(existing), listOf(Book(bookUrl = "new-source", name = "name")))
        assertEquals("old-source", restored.bookUrl)
    }

    @Test
    fun `brand new legacy import backfills only unique book`() {
        val old = ReadRecord(bookName = "name")
        val book = Book(bookUrl = "url", name = "name", author = "author", coverUrl = "cover", origin = "source")
        val restored = ReadRecordRestore.resolve(old, emptyList(), listOf(book))
        assertEquals("url", restored.bookUrl)
        assertEquals("author", restored.author)
        assertEquals("source", restored.coverOrigin)
        assertEquals(ReadRecord.legacyBookUrl("name"), ReadRecordRestore.resolve(
            old, emptyList(), listOf(book, book.copy(bookUrl = "other", author = "other"))
        ).bookUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ambiguous old backup must not create another counted row`() {
        val old = ReadRecord(deviceId = "device", bookName = "name")
        ReadRecordRestore.resolve(old, listOf(old.copy(bookUrl = "one"), old.copy(bookUrl = "two")), emptyList())
    }

    @Test
    fun `explicit null optional fields in legacy JSON are normalized`() {
        val record = GSON.fromJson(
            """{"bookName":"name","author":null,"coverUrl":null,"coverOrigin":null,"bookUrl":null}""",
            ReadRecord::class.java,
        )
        val result = ReadRecordRestore.resolve(record, emptyList(), emptyList())
        assertEquals(ReadRecord.legacyBookUrl("name"), result.bookUrl)
        assertEquals("", result.author)
        assertEquals("", result.coverUrl)
        assertEquals("", result.coverOrigin)
    }

    @Test
    fun `modern backup keeps url and snapshot instead of following same title`() {
        val record = ReadRecord(bookUrl = "real", bookName = "name", author = "A", coverUrl = "saved", coverOrigin = "saved-source")
        val result = ReadRecordRestore.resolve(record, emptyList(), listOf(Book(bookUrl = "other", name = "name", author = "B")))
        assertEquals(record, result)
    }
}
