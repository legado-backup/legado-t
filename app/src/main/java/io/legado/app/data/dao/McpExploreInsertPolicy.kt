package io.legado.app.data.dao

import io.legado.app.data.entities.ExploreContainer

/** 仅供 MCP 新增:所有行由 Room 原子分配 ID,调用者的 id/sortOrder 不参与覆盖或插队。 */
internal object McpExploreInsertPolicy {
    fun newRows(containers: List<ExploreContainer>, maxOrder: Int): List<ExploreContainer> {
        require(containers.isNotEmpty()) { "发现容器不能为空" }
        val start = maxOrder.toLong() + 1L
        require(start + containers.size - 1L <= Int.MAX_VALUE) { "发现容器排序值已达上限" }
        return containers.mapIndexed { index, container ->
            container.copy(id = 0L, sortOrder = (start + index).toInt())
        }
    }
}
