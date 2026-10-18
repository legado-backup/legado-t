package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.ReadRecord
import io.legado.app.data.entities.ReadRecordShow

// 封面与来源从同一快照读取，避免把 A 源的图片交给 B 源的请求头。
private const val SHOW_RECORDS = """
    select r.bookUrl, r.bookName, t.readTime, t.lastRead,
        coalesce(nullif(a.author, ''), b.author, '') as author,
        coalesce(nullif(c.coverUrl, ''), nullif(b.customCoverUrl, ''), b.coverUrl) as coverUrl,
        case when c.coverUrl != '' then c.coverOrigin
             when coalesce(b.customCoverUrl, '') != '' then ''
             else coalesce(b.origin, '') end as coverOrigin
    from (select bookUrl, sum(readTime) as readTime, max(lastRead) as lastRead
          from readRecord group by bookUrl) t
    join readRecord r on r.bookUrl = t.bookUrl and r.deviceId = (
        select deviceId from readRecord where bookUrl = t.bookUrl
        order by lastRead desc, deviceId limit 1)
    left join readRecord a on a.bookUrl = t.bookUrl and a.deviceId = (
        select deviceId from readRecord where bookUrl = t.bookUrl and author != ''
        order by lastRead desc, deviceId limit 1)
    left join readRecord c on c.bookUrl = t.bookUrl and c.deviceId = (
        select deviceId from readRecord where bookUrl = t.bookUrl and coverUrl != ''
        order by lastRead desc, deviceId limit 1)
    left join books b on b.bookUrl = t.bookUrl
"""

@Dao
interface ReadRecordDao {

    @get:Query("select * from readRecord")
    val all: List<ReadRecord>

    @get:Query(SHOW_RECORDS + " order by r.bookName collate localized")
    val allShow: List<ReadRecordShow>

    @get:Query("select coalesce(sum(readTime), 0) from readRecord")
    val allTime: Long

    @Query(SHOW_RECORDS + " where r.bookName like '%' || :searchKey || '%' order by r.bookName collate localized")
    fun search(searchKey: String): List<ReadRecordShow>

    @Query("select * from readRecord where deviceId = :deviceId and bookUrl = :bookUrl limit 1")
    fun getRecord(deviceId: String, bookUrl: String): ReadRecord?

    @Query("select * from readRecord where deviceId = :deviceId and bookName = :bookName")
    fun getRecordsByName(deviceId: String, bookName: String): List<ReadRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg readRecord: ReadRecord)

    /** 备份是同一设备累计值的快照，不能覆盖较新的时长和非空元数据。 */
    @Transaction
    fun merge(record: ReadRecord) {
        val existing = getRecord(record.deviceId, record.bookUrl)
        insert(existing?.merge(record) ?: record)
    }

    /** 阅读会话只提交增量，避免切书或多个阅读器覆盖排队中的累计值。 */
    @Transaction
    fun addTime(snapshot: ReadRecord, elapsed: Long) {
        val existing = getRecord(snapshot.deviceId, snapshot.bookUrl)
        val total = (existing?.readTime ?: 0L) + elapsed.coerceAtLeast(0L)
        val metadata = existing?.merge(snapshot) ?: snapshot
        insert(metadata.copy(readTime = total))
    }

    @Update
    fun update(vararg record: ReadRecord)

    @Delete
    fun delete(vararg record: ReadRecord)

    @Query("delete from readRecord")
    fun clear()

    @Query("delete from readRecord where bookUrl = :bookUrl")
    fun deleteByBookUrl(bookUrl: String)
}
