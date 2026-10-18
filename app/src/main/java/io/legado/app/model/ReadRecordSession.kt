package io.legado.app.model

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.ReadRecord

/**
 * 固定结算时的书籍快照后再交给存储队列；不缓存数据库累计值。
 * 页面和后台朗读可同时走时，但重叠的区间只累计一次。
 */
internal class ReadRecordSession(
    private val deviceId: () -> String,
    enabled: () -> Boolean,
    private val save: (ReadRecord, Long) -> Unit,
    private val elapsedTime: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wallTime: () -> Long = System::currentTimeMillis,
) {
    private var book: Book? = null
    private var startedAt: Long? = null
    private val activeReasons = mutableSetOf<String>()
    private var recordingEnabled = enabled()

    @Synchronized
    fun setBook(book: Book?) {
        if (this.book === book) return
        checkpoint()
        this.book = book
        startedAt = if (canRun()) elapsedTime() else null
    }

    /** 切换开关时先按旧状态结算，保留页面/播放状态，不追补关闭期间的时长。 */
    @Synchronized
    fun setEnabled(enabled: Boolean) {
        if (recordingEnabled == enabled) return
        checkpoint()
        recordingEnabled = enabled
        startedAt = if (canRun()) elapsedTime() else null
    }

    private fun canRun() = recordingEnabled && activeReasons.isNotEmpty() && book != null

    @Synchronized
    fun setActive(reason: String, active: Boolean) {
        if (active) {
            activeReasons.add(reason)
            if (startedAt == null && canRun()) startedAt = elapsedTime()
        } else {
            activeReasons.remove(reason)
            if (activeReasons.isEmpty()) checkpoint(stop = true)
        }
    }

    @Synchronized
    fun checkpoint(stop: Boolean = false) {
        val now = elapsedTime()
        val elapsed = startedAt?.let { (now - it).coerceAtLeast(0) } ?: 0L
        startedAt = if (!stop && canRun()) now else null
        val book = book ?: return
        if (!recordingEnabled || elapsed == 0L) return
        save(snapshot(book), elapsed)
    }

    /** 详情异步加载后立即保留快照，不依赖下一次翻页。 */
    @Synchronized
    fun refreshMetadata() {
        val book = book ?: return
        if (recordingEnabled) save(snapshot(book), 0L)
    }

    private fun snapshot(book: Book) = ReadRecord(
        deviceId = deviceId(),
        bookName = book.name,
        bookUrl = book.bookUrl,
        author = book.author,
        coverUrl = book.getDisplayCover().orEmpty(),
        coverOrigin = book.getCoverSourceOrigin().orEmpty(),
        lastRead = wallTime(),
    )
}
