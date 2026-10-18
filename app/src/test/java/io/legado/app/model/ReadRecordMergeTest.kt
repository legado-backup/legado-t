package io.legado.app.model

import io.legado.app.data.entities.ReadRecord
import io.legado.app.utils.GSON
import org.junit.Assert.*
import org.junit.Test

class ReadRecordMergeTest {
    private fun record(time: Long, last: Long, author: String = "", cover: String = "", origin: String = "") =
        ReadRecord(deviceId = "device", bookName = "name", bookUrl = "url", readTime = time,
            lastRead = last, author = author, coverUrl = cover, coverOrigin = origin)

    @Test
    fun `stale backup cannot reduce cumulative time or clear snapshot`() {
        val existing = record(100, 200, "author", "new-cover", "new-source")
        val restored = existing.merge(record(50, 100))
        assertEquals(existing, restored)
        assertEquals(restored, restored.merge(record(50, 100)))
    }

    @Test
    fun `equal time backup can complete missing metadata`() {
        val restored = record(100, 200).merge(record(100, 200, "author", "cover", "source"))
        assertEquals(100L, restored.readTime)
        assertEquals("author", restored.author)
        assertEquals("cover", restored.coverUrl)
        assertEquals("source", restored.coverOrigin)
    }

    @Test
    fun `cover and origin always come from same snapshot`() {
        val restored = record(100, 100, cover = "cover", origin = "source")
            .merge(record(50, 200, "new-author"))
        assertEquals(100L, restored.readTime)
        assertEquals(200L, restored.lastRead)
        assertEquals("new-author", restored.author)
        assertEquals("cover", restored.coverUrl)
        assertEquals("source", restored.coverOrigin)
        assertEquals("", restored.merge(record(100, 300, cover = "custom")).coverOrigin)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `same name different book cannot merge`() {
        record(1, 1).merge(record(1, 1).copy(bookUrl = "other"))
    }

    @Test
    fun `legacy keys are deterministic utf8 and do not collide on punctuation`() {
        assertEquals("legacy:E4B9A6", ReadRecord.legacyBookUrl("书"))
        assertNotEquals(ReadRecord.legacyBookUrl("a:b"), ReadRecord.legacyBookUrl("ab"))
    }

    @Test
    fun `old backups without new fields are readable`() {
        val record = GSON.fromJson("""{"deviceId":"","bookName":"old","readTime":42,"lastRead":12}""", ReadRecord::class.java)
        assertEquals("", record.bookUrl)
        assertEquals("", record.author)
        assertEquals("", record.coverOrigin)
        assertEquals(42L, record.readTime)
    }
}
