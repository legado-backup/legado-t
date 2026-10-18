package io.legado.app.web.mcp

import io.legado.app.data.appDb
import io.legado.app.data.entities.ExploreContainer
import io.legado.app.help.source.ExploreContainerHelp
import io.legado.app.help.source.exploreKinds
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * 发现页容器管理工具:配置发现卡片,不负责抓取发现书籍。
 * 新增/批量新增会按书源当前 exploreKinds 校验分类并保存 URL 快照;
 * 更新/删除与 App 管理页一致,指向变更或删除时清理 ExploreContainerHelp 缓存。
 */
internal fun Server.registerExploreTools() {
    serverAddListExploreContainers()
    serverAddAddExploreContainer()
    serverAddAddExploreContainers()
    serverAddUpdateExploreContainer()
    serverAddDeleteExploreContainer()
}

private fun Server.serverAddListExploreContainers() {
    addTool(
        name = "list_explore_containers",
        description = "列出阅读 App 内的发现页容器卡片(摘要:id/标题/书源/分类/样式/数量/启用/分组/排序)。" +
            "按 search 过滤标题、书源名、书源 URL 或分类名;按 enabled 过滤启用状态。",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("search", stringProp("标题/书源名/书源 URL/分类名子串过滤,大小写不敏感"))
                putJsonObject("enabled") {
                    put("type", "boolean")
                    put("description", "true 只列启用,false 只列停用,缺省列全部")
                }
            },
            required = emptyList(),
        ),
        toolAnnotations = ToolAnnotations(readOnlyHint = true),
    ) { request ->
        try {
            val search = request.arguments.str("search")?.trim()?.takeIf { it.isNotEmpty() }
            val enabled = request.arguments.bool("enabled")
            val summaries = appDb.exploreContainerDao.all
                .asSequence()
                .filter { enabled == null || it.enabled == enabled }
                .map { it.toMcpSummary() }
                .filter { summary ->
                    search.isNullOrEmpty() || summary.values.any { value ->
                        value.toString().contains(search, ignoreCase = true)
                    }
                }
                .toList()
            if (summaries.isEmpty()) {
                ok("(App 内无匹配的发现容器)")
            } else {
                ok("共 ${summaries.size} 条\n" + McpFormat.truncate(McpFormat.toPrettyJson(summaries)))
            }
        } catch (e: Exception) {
            err(e.localizedMessage ?: e.toString())
        }
    }
}

private fun Server.serverAddAddExploreContainer() {
    addTool(
        name = "add_explore_container",
        description = "新增一个发现页容器卡片。container 为 JSON 字符串,sourceUrl 必填且书源须启用发现;" +
            "支持kindTitle、kindUrl或kindTitles/kindUrls,省略分类取首项;allKinds=true保存全部分类,dynamic=true动态全部分类。" +
            "新增忽略传入id/sortOrder,由数据库自增ID并追加末尾;含分隔符的分类快照自动改为动态全部分类;不会抓取书籍。",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("container", stringProp("JSON对象字符串,必填sourceUrl;分类缺省首项,allKinds或dynamic可选"))
            },
            required = listOf("container"),
        ),
        toolAnnotations = ToolAnnotations(
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = false,
            openWorldHint = false,
        ),
    ) { request ->
        try {
            val json = request.arguments.str("container")
                ?: return@addTool err("参数container不能为空")
            val input = McpExploreInputs.parse(json)
            val prepared = prepareContainer(input)
            val inserted = appDb.exploreContainerDao.appendNew(listOf(prepared)).single()
            ok("已新增发现容器:${inserted.getDisplayTitle()}(id: ${inserted.id})")
        } catch (e: Exception) {
            err(e.localizedMessage ?: e.toString())
        }
    }
}

private fun Server.serverAddAddExploreContainers() {
    addTool(
        name = "add_explore_containers",
        description = "批量新增发现卡片(最多50源/配置)。containers(JSON数组字符串)与sourceUrls(字符串数组)二选一;" +
            "sourceUrls默认每源一张全部分类卡,dynamic=true保留运行时全部分类;containers每项同单条,allKinds=true可整批选全部。" +
            "可设置groupName/style/listCount/customTitle/enabled。严格模式:未启用发现/无有效分类等无效源报错,不静默跳过;全部校验后单事务写入,失败整批不写入。" +
            "忽略传入id/sortOrder,数据库自增ID,按输入顺序连续追加;不抓取书籍。",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("containers", stringProp("ExploreContainer JSON数组字符串;与sourceUrls二选一"))
                putJsonObject("sourceUrls") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("description", "已有且启用发现的书源URL;自动每源生成一张全部分类卡")
                }
                putJsonObject("allKinds") { put("type", "boolean") }
                putJsonObject("dynamic") { put("type", "boolean") }
                put("groupName", stringProp("sourceUrls模式的卡片分组"))
                put("customTitle", stringProp("sourceUrls模式的自定义标题"))
                putJsonObject("style") { put("type", "integer"); put("description", "0横滑/1列表") }
                putJsonObject("listCount") { put("type", "integer"); put("description", "1至20") }
                putJsonObject("enabled") { put("type", "boolean") }
            },
            required = emptyList(),
        ),
        toolAnnotations = ToolAnnotations(
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = false,
            openWorldHint = false,
        ),
    ) { request ->
        try {
            val inputs = McpExploreInputs.parseBatch(request.arguments?.toString() ?: "{}")
            val prepared = inputs.map { prepareContainer(it) }
            val inserted = appDb.exploreContainerDao.appendNew(prepared)
            ok("已新增 ${inserted.size} 个发现容器\n" + inserted.joinToString("\n") {
                "- ${it.getDisplayTitle()}(id: ${it.id})"
            })
        } catch (e: Exception) {
            err(e.localizedMessage ?: e.toString())
        }
    }
}

private fun Server.serverAddUpdateExploreContainer() {
    addTool(
        name = "update_explore_container",
        description = "更新一个已有发现页容器。container 为完整 ExploreContainer JSON,按 id 定位;" +
            "sourceUrl 或分类指向变化时会清理旧卡片缓存。更新不会抓取书籍。",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("container", stringProp("包含已有 id 的完整 ExploreContainer JSON 对象"))
            },
            required = listOf("container"),
        ),
        toolAnnotations = ToolAnnotations(
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = true,
            openWorldHint = false,
        ),
    ) { request ->
        try {
            val json = request.arguments.str("container")
                ?: return@addTool err("参数container不能为空")
            val input = McpExploreInputs.parse(json)
            if (input.container.id <= 0) return@addTool err("参数container.id必须为正整数")
            val current = appDb.exploreContainerDao.getById(input.container.id)
                ?: return@addTool err("未找到发现容器:${input.container.id}")
            val prepared = prepareContainer(input).copy(id = current.id)
            appDb.exploreContainerDao.update(prepared)
            if (!McpExploreInputs.sameTargetAndKinds(current, prepared)) {
                ExploreContainerHelp.removeCache(prepared.id)
            }
            ok("已更新发现容器:${prepared.getDisplayTitle()}(id: ${prepared.id})")
        } catch (e: Exception) {
            err(e.localizedMessage ?: e.toString())
        }
    }
}

private fun Server.serverAddDeleteExploreContainer() {
    addTool(
        name = "delete_explore_container",
        description = "按 id 删除发现页容器卡片。ids 可传一个或多个整数;删除后同时清理卡片书籍缓存。",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("ids") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "integer") }
                    put("description", "发现容器 id 列表(list_explore_containers 返回)")
                }
            },
            required = listOf("ids"),
        ),
        toolAnnotations = ToolAnnotations(
            readOnlyHint = false,
            destructiveHint = true,
            idempotentHint = true,
            openWorldHint = false,
        ),
    ) { request ->
        try {
            val ids = request.arguments?.get("ids")?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.toLongOrNull() }
                ?.filter { it > 0 }
                ?.distinct()
                .orEmpty()
            if (ids.isEmpty()) return@addTool err("参数ids不能为空")
            val containers = ids.mapNotNull { appDb.exploreContainerDao.getById(it) }
            if (containers.isEmpty()) return@addTool err("未找到指定的发现容器")
            appDb.exploreContainerDao.delete(*containers.toTypedArray())
            containers.forEach { ExploreContainerHelp.removeCache(it.id) }
            val missing = ids.size - containers.size
            val suffix = if (missing == 0) "" else "(另有 $missing 个 id 不存在)"
            ok("已删除 ${containers.size} 个发现容器$suffix")
        } catch (e: Exception) {
            err(e.localizedMessage ?: e.toString())
        }
    }
}

private suspend fun prepareContainer(input: McpExploreInput): ExploreContainer {
    val source = appDb.bookSourceDao.getBookSource(input.container.sourceUrl)
        ?: throw IllegalArgumentException("未找到书源:${input.container.sourceUrl}")
    require(source.enabledExplore) { "书源未启用发现:${input.container.sourceUrl}" }
    return McpExploreInputs.prepare(input, source.bookSourceName, source.enabledExplore, source.exploreKinds())
}

private fun ExploreContainer.toMcpSummary(): Map<String, Any?> = mapOf(
    "id" to id,
    "title" to getDisplayTitle(),
    "customTitle" to (customTitle ?: ""),
    "sourceUrl" to sourceUrl,
    "sourceName" to sourceName,
    "kindTitle" to kindTitle,
    "kindUrl" to kindUrl,
    "kindTitles" to kindTitles,
    "kindUrls" to kindUrls,
    "style" to style,
    "styleName" to if (style == ExploreContainer.STYLE_LIST) "list" else "flow",
    "listCount" to listCount,
    "enabled" to enabled,
    "groupName" to groupName,
    "sortOrder" to sortOrder,
)
