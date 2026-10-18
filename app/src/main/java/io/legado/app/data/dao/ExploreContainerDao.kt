package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.legado.app.data.entities.ExploreContainer
import io.legado.app.help.source.ExploreContainerHelp
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Dao
interface ExploreContainerDao {

    @get:Query("SELECT * FROM exploreContainers ORDER BY sortOrder ASC")
    val all: List<ExploreContainer>

    @Query("SELECT * FROM exploreContainers ORDER BY sortOrder ASC")
    fun flowAll(): Flow<List<ExploreContainer>>

    @Query("SELECT * FROM exploreContainers WHERE enabled = 1 ORDER BY sortOrder ASC")
    fun flowEnabled(): Flow<List<ExploreContainer>>

    @Query("SELECT * FROM exploreContainers WHERE id = :id")
    fun getById(id: Long): ExploreContainer?

    @get:Query("SELECT ifnull(min(sortOrder), 0) FROM exploreContainers")
    val minOrder: Int

    @get:Query("SELECT ifnull(max(sortOrder), 0) FROM exploreContainers")
    val maxOrder: Int

    @Query("select distinct groupName from exploreContainers where trim(groupName) <> ''")
    fun flowGroupsUnProcessed(): Flow<List<String>>

    @get:Query("select * from exploreContainers where trim(groupName) = ''")
    val noGroup: List<ExploreContainer>

    @Query("select * from exploreContainers where groupName like '%' || :group || '%'")
    fun getByGroup(group: String): List<ExploreContainer>

    fun flowGroups(): Flow<List<String>> {
        return flowGroupsUnProcessed().map {
            ExploreContainerHelp.dealGroups(it)
        }.flowOn(IO)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg container: ExploreContainer): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertNew(vararg container: ExploreContainer): List<Long>

    /** 校验在工具层完成;排序分配、整批写入、Room 自增 ID 均在同一事务中完成。 */
    @Transaction
    fun appendNew(containers: List<ExploreContainer>): List<ExploreContainer> {
        val rows = McpExploreInsertPolicy.newRows(containers, maxOrder)
        val ids = insertNew(*rows.toTypedArray())
        return rows.mapIndexed { index, row -> row.copy(id = ids[index]) }
    }

    @Update
    fun update(vararg container: ExploreContainer)

    @Delete
    fun delete(vararg container: ExploreContainer)
}
