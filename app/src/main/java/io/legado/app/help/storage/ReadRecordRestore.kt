package io.legado.app.help.storage

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.ReadRecord

/** 为没有稳定书籍键的旧备份复用已迁移身份，防止重新入架/换源后把同一历史快照计两次。 */
internal object ReadRecordRestore {
    fun resolve(input: ReadRecord, existing: List<ReadRecord>, books: List<Book>): ReadRecord {
        // 显式 null 的旧 JSON 也可以恢复；正常 Kotlin/Gson 默认构造均为非空。
        @Suppress("USELESS_ELVIS")
        val record = input.copy(
            author = input.author ?: "",
            coverUrl = input.coverUrl ?: "",
            coverOrigin = input.coverOrigin ?: "",
            bookUrl = input.bookUrl ?: "",
        )
        if (record.bookUrl.isBlank()) {
            val legacyKey = ReadRecord.legacyBookUrl(record.bookName)
            val candidates = existing.filter {
                it.deviceId == record.deviceId && it.bookName == record.bookName &&
                    (record.author.isBlank() || it.author.isBlank() || it.author == record.author)
            }
            val previous = candidates.firstOrNull { it.bookUrl == legacyKey }
                ?: candidates.singleOrNull()
            require(previous != null || candidates.isEmpty()) {
                "旧阅读记录存在多个身份，无法安全合并：${record.bookName}"
            }
            val match = books.filter {
                it.name == record.bookName && (record.author.isBlank() || it.author == record.author)
            }.singleOrNull()
            record.bookUrl = previous?.bookUrl ?: match?.bookUrl ?: legacyKey
        }
        val book = books.firstOrNull { it.bookUrl == record.bookUrl }
        if (book != null) {
            if (record.author.isBlank()) record.author = book.author
            if (record.coverUrl.isBlank()) {
                record.coverUrl = book.getDisplayCover().orEmpty()
                record.coverOrigin = book.getCoverSourceOrigin().orEmpty()
            }
        }
        return record
    }
}
