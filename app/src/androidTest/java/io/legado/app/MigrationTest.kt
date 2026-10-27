package io.legado.app

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.AppDatabase
import io.legado.app.data.DatabaseMigrations
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.ExploreContainer
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import io.legado.app.data.entities.ReadRecord
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val testDb = "migration-test"
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrateAll() {
        helper.createDatabase(testDb, 50).close()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, testDb)
            .addMigrations(*DatabaseMigrations.migrations)
            .build()
        try {
            db.openHelper.writableDatabase
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate94AddsOptionalRssSearchUrlWithoutChangingExistingSources() {
        helper.createDatabase(testDb, 94).apply {
            execSQL(
                "INSERT INTO rssSources(sourceUrl, sourceName, sourceIcon, enabled, singleUrl) " +
                    "VALUES('https://rss.test/feed', 'RSS', '', 1, 0)"
            )
            close()
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, testDb).build()
        try {
            db.openHelper.writableDatabase
            db.query(SimpleSQLiteQuery("SELECT sourceUrl, searchUrl FROM rssSources")).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("https://rss.test/feed", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate93KeepsTotalsAndBackfillsOnlyUnambiguousBooks() {
        helper.createDatabase(testDb, 93).apply {
            execSQL("INSERT INTO books(bookUrl,name,author,coverUrl,origin) VALUES('one','唯一','作者','cover','source')")
            execSQL("INSERT INTO books(bookUrl,name,author) VALUES('two','同名','甲'),('three','同名','乙')")
            execSQL("INSERT INTO books(bookUrl,name,author,customCoverUrl,origin) VALUES('custom','自定义','作者','local-cover','source')")
            execSQL("INSERT INTO readRecord VALUES('','唯一',100,1),('device','唯一',50,2),('other','同名',70,3),('device','已删除',10,4),('device','自定义',20,5)")
            close()
        }
        helper.runMigrationsAndValidate(testDb, 94, true, DatabaseMigrations.migration_93_94).use { db ->
            db.query("SELECT count(*),sum(readTime) FROM readRecord").use {
                assertTrue(it.moveToFirst())
                assertEquals(5, it.getInt(0))
                assertEquals(250L, it.getLong(1))
            }
            db.query("SELECT deviceId,author,coverUrl,coverOrigin FROM readRecord WHERE bookUrl='one' ORDER BY deviceId").use {
                assertTrue(it.moveToFirst())
                assertEquals("", it.getString(0)) // legacy device provenance is deliberately retained
                assertEquals("作者", it.getString(1))
                assertEquals("cover", it.getString(2))
                assertEquals("source", it.getString(3))
                assertTrue(it.moveToNext())
                assertEquals("device", it.getString(0))
            }
            db.query("SELECT bookUrl,author,coverUrl FROM readRecord WHERE bookName='同名'").use {
                assertTrue(it.moveToFirst())
                assertEquals(ReadRecord.legacyBookUrl("同名"), it.getString(0))
                assertEquals("", it.getString(1))
                assertEquals("", it.getString(2))
            }
            db.query("SELECT coverUrl,coverOrigin FROM readRecord WHERE bookUrl='custom'").use {
                assertTrue(it.moveToFirst())
                assertEquals("local-cover", it.getString(0))
                assertEquals("", it.getString(1))
            }
            // Existing view survives the table rebuild and remains queryable.
            db.query("SELECT hasLoginUrl FROM book_sources_part").close()
        }
    }

    @Test
    fun concurrentMcpBatchesAppendWithoutReplacingCardsOrReusingOrder() {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val workers = Executors.newFixedThreadPool(2)
        try {
            val dao = db.exploreContainerDao
            val original = ExploreContainer(id = 1L, sourceUrl = "existing", sortOrder = 5)
            dao.insert(original)
            val writes = workers.invokeAll((1..2).map { batch ->
                Callable {
                    dao.appendNew((1..3).map {
                        ExploreContainer(id = 1L, sourceUrl = "batch-$batch-$it", sortOrder = -100)
                    })
                }
            })
            val inserted = writes.flatMap { it.get(30, TimeUnit.SECONDS) }
            assertEquals(6, inserted.map { it.id }.toSet().size)
            assertEquals((6..11).toList(), inserted.map { it.sortOrder }.sorted())
            assertEquals(original, dao.getById(1))
            assertEquals(7, dao.all.size)
            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                dao.insertNew(ExploreContainer(id = 999, sourceUrl = "rolled-back"), original)
            }
            assertNull(dao.getById(999))
        } finally {
            workers.shutdownNow()
            db.close()
        }
    }

    @Test
    fun daoKeepsSameNamesSeparateAndMergesSnapshotsIdempotently() {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.readRecordDao
            db.bookDao.insert(Book(bookUrl = "first", name = "same", author = "A", coverUrl = "current", origin = "source"))
            db.bookDao.insert(Book(bookUrl = "second", name = "same", author = "B"))
            dao.insert(ReadRecord("one", "same", 100, 100, bookUrl = "first"))
            dao.insert(ReadRecord("two", "same", 20, 200, "A", "snapshot", "first", "snapshot-source"))
            dao.insert(ReadRecord("one", "same", 30, 100, "B", bookUrl = "second"))
            val records = dao.search("").associateBy { it.bookUrl }
            assertEquals(2, records.size)
            assertEquals(120L, records.getValue("first").readTime)
            assertEquals("A", records.getValue("first").author)
            assertEquals("snapshot", records.getValue("first").coverUrl)
            assertEquals("snapshot-source", records.getValue("first").coverOrigin)
            val backup = ReadRecord("one", "same", 90, 300, "A", "custom", "first")
            dao.merge(backup)
            dao.merge(backup)
            assertEquals(100L, dao.getRecord("one", "first")?.readTime)
            assertEquals("custom", dao.getRecord("one", "first")?.coverUrl)
            dao.addTime(backup.copy(readTime = 0), 5)
            dao.addTime(backup.copy(readTime = 0), 7)
            assertEquals(112L, dao.getRecord("one", "first")?.readTime)
            dao.deleteByBookUrl("second")
            assertEquals(1, dao.allShow.size)
            assertEquals(132L, dao.allTime)
        } finally {
            db.close()
        }
    }
}
