package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "readRecord",
    primaryKeys = ["deviceId", "bookUrl"],
    indices = [Index(value = ["bookUrl", "lastRead"])]
)
data class ReadRecord(
    var deviceId: String = "",
    var bookName: String = "",
    @ColumnInfo(defaultValue = "0")
    var readTime: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    var lastRead: Long = System.currentTimeMillis(),
    /** 作者与封面快照在书籍被移出书架后仍然保留。 */
    @ColumnInfo(defaultValue = "")
    var author: String = "",
    @ColumnInfo(defaultValue = "")
    var coverUrl: String = "",
    @ColumnInfo(defaultValue = "")
    var bookUrl: String = "",
    /** 空字符串表示自定义/本地封面，不应使用书源请求头。 */
    @ColumnInfo(defaultValue = "")
    var coverOrigin: String = ""
) {
    companion object {
        /** 旧记录无法唯一匹配书籍时保留独立身份，不猜测同名书的作者。 */
        fun legacyBookUrl(bookName: String): String = "legacy:" +
            bookName.toByteArray(Charsets.UTF_8).joinToString("") {
                (it.toInt() and 0xff).toString(16).padStart(2, '0').uppercase()
            }
    }

    /** 同一设备备份是累计计数的快照，恢复取最大值而不是相加。 */
    fun merge(other: ReadRecord): ReadRecord {
        require(deviceId == other.deviceId && bookUrl == other.bookUrl)
        val newer = if (other.lastRead >= lastRead) other else this
        val older = if (newer === this) other else this
        val cover = if (newer.coverUrl.isNotBlank()) newer else older
        return newer.copy(
            readTime = maxOf(readTime, other.readTime),
            lastRead = maxOf(lastRead, other.lastRead),
            author = newer.author.ifBlank { older.author },
            coverUrl = cover.coverUrl,
            coverOrigin = cover.coverOrigin,
        )
    }
}
