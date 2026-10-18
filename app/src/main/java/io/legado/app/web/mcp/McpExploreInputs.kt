package io.legado.app.web.mcp

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.data.entities.ExploreContainer
import io.legado.app.data.entities.rule.ExploreKind

internal enum class McpExploreKindMode { SELECTED, ALL, DYNAMIC }

internal data class McpExploreInput(
    val container: ExploreContainer,
    val mode: McpExploreKindMode = McpExploreKindMode.SELECTED,
)

/** 纯输入/分类处理,不执行 JS、不访问数据库。禁止 Gson 将显式 null 填进 Kotlin 非空字段。 */
internal object McpExploreInputs {
    const val MAX_BATCH_SIZE = 50
    private val separators = Regex("[,;，；]")

    fun parse(json: String): McpExploreInput = parseObject(objectOf(JsonParser.parseString(json)))

    /** containers 保持已有 JSON 字符串协议;sourceUrls 是直接的 JSON 数组,默认每源一张全部分类卡。 */
    fun parseBatch(arguments: String): List<McpExploreInput> {
        val args = objectOf(JsonParser.parseString(arguments))
        require(args.has("containers") xor args.has("sourceUrls")) {
            "containers与sourceUrls必须且只能提供一个"
        }
        if (args.has("containers")) {
            val array = JsonParser.parseString(string(args, "containers"))
            require(array.isJsonArray) { "containers必须为ExploreContainer JSON数组" }
            requireBatchSize(array.asJsonArray.size())
            val batchMode = mode(args)
            return array.asJsonArray.map { element ->
                val input = parseObject(objectOf(element))
                if (batchMode == McpExploreKindMode.SELECTED) input else {
                    require(input.mode == McpExploreKindMode.SELECTED || input.mode == batchMode) {
                        "整批分类模式与单项模式冲突"
                    }
                    requireNoSelection(input.container)
                    input.copy(mode = batchMode)
                }
            }
        }
        val urls = args.get("sourceUrls")
        require(urls.isJsonArray) { "sourceUrls必须为字符串数组" }
        requireBatchSize(urls.asJsonArray.size())
        val sourceUrls = urls.asJsonArray.map {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "sourceUrls每项必须为非空字符串" }
            it.asString.trim().also { url -> require(url.isNotEmpty()) { "sourceUrl不能为空" } }
        }.distinct()
        val inputMode = if (boolean(args, "dynamic", false)) McpExploreKindMode.DYNAMIC else McpExploreKindMode.ALL
        return sourceUrls.map { url ->
            val config = args.deepCopy().apply { addProperty("sourceUrl", url) }
            val input = parseObject(config)
            requireNoSelection(input.container)
            input.copy(mode = inputMode)
        }
    }

    /** 单条缺省首个有效分类;ALL 固化全部分类;DYNAMIC 保留全部分类的运行时语义。 */
    fun prepare(
        input: McpExploreInput,
        sourceName: String,
        enabledExplore: Boolean,
        sourceKinds: List<ExploreKind>,
    ): ExploreContainer {
        require(enabledExplore) { "书源未启用发现:${input.container.sourceUrl}" }
        val kinds = sourceKinds.filter { !it.url.isNullOrBlank() && !it.title.startsWith("ERROR:") }
            .distinctBy { it.title to it.url }
        require(kinds.isNotEmpty()) { "书源中没有有效发现分类:${input.container.sourceUrl}" }
        val container = input.container
        val selected = if (input.mode == McpExploreKindMode.SELECTED) {
            selectKinds(container, kinds)
        } else {
            requireNoSelection(container)
            kinds
        }
        // 现有持久化格式用分组分隔符切分标题/URL。不能无损表示时保留动态全部分类,
        // 不把 /all<,-{{page}}>.html 等 URL 保存成会被误拆的快照。
        val dynamic = input.mode == McpExploreKindMode.DYNAMIC || selected.any {
            separators.containsMatchIn(it.title) || separators.containsMatchIn(it.url!!)
        }
        val active = if (input.mode == McpExploreKindMode.SELECTED && container.kindTitle.isNotBlank()) {
            selected.firstOrNull { it.title == container.kindTitle && it.url == container.kindUrl }
                ?: selected.firstOrNull { it.title == container.kindTitle }
                ?: selected.first()
        } else selected.first()
        return container.copy(
            sourceName = sourceName,
            kindTitle = active.title,
            kindUrl = active.url!!,
            kindTitles = if (dynamic) "" else selected.joinToString(",") { it.title },
            kindUrls = if (dynamic) "" else selected.joinToString(",") { it.url!! },
        )
    }

    fun sameTargetAndKinds(first: ExploreContainer, second: ExploreContainer): Boolean =
        first.sourceUrl == second.sourceUrl && first.kindTitle == second.kindTitle &&
            first.kindUrl == second.kindUrl && first.kindTitles == second.kindTitles &&
            first.kindUrls == second.kindUrls

    private fun selectKinds(container: ExploreContainer, kinds: List<ExploreKind>): List<ExploreKind> {
        if (container.kindTitles.isBlank()) {
            return listOf(resolveKind(container.kindTitle, container.kindUrl, kinds))
        }
        val titles = container.kindTitles.split(separators).map(String::trim).filter(String::isNotEmpty)
        require(titles.isNotEmpty()) { "kindTitles必须包含有效标题" }
        val urls = if (container.kindUrls.isBlank()) emptyList() else {
            container.kindUrls.split(separators).map(String::trim)
        }
        require(urls.isEmpty() || urls.size == titles.size) { "kindTitles与kindUrls数量必须一致;含分隔符请使用dynamic" }
        return titles.mapIndexed { index, title ->
            resolveKind(title, urls.getOrNull(index).orEmpty(), kinds)
        }.distinctBy { it.title to it.url }
    }

    private fun resolveKind(title: String, url: String, kinds: List<ExploreKind>): ExploreKind {
        if (title.isBlank() && url.isBlank()) return kinds.first()
        if (title.isBlank()) {
            return kinds.firstOrNull { it.url == url }
                ?: throw IllegalArgumentException("书源中未找到有效分类URL:$url")
        }
        return kinds.firstOrNull { it.title == title && it.url == url }
            ?: kinds.firstOrNull { it.title == title }
            ?: if (url.isNotBlank()) ExploreKind(title, url) else {
                throw IllegalArgumentException("书源中未找到有效分类:$title")
            }
    }

    private fun parseObject(obj: JsonObject): McpExploreInput {
        val sourceUrl = string(obj, "sourceUrl").trim()
        require(sourceUrl.isNotEmpty()) { "sourceUrl不能为空" }
        val style = integer(obj, "style", ExploreContainer.STYLE_FLOW)
        require(style == ExploreContainer.STYLE_FLOW || style == ExploreContainer.STYLE_LIST) { "style必须为0或1" }
        val listCount = integer(obj, "listCount", 3)
        require(listCount in 1..20) { "listCount必须在1至20之间" }
        val container = ExploreContainer(
            id = long(obj, "id", 0),
            sourceUrl = sourceUrl,
            sourceName = string(obj, "sourceName", ""),
            kindTitle = string(obj, "kindTitle", "").trim(),
            kindUrl = string(obj, "kindUrl", "").trim(),
            kindTitles = string(obj, "kindTitles", ""),
            kindUrls = string(obj, "kindUrls", ""),
            customTitle = if (obj.has("customTitle") && !obj.get("customTitle").isJsonNull) {
                string(obj, "customTitle")
            } else null,
            style = style,
            listCount = listCount,
            sortOrder = integer(obj, "sortOrder", 0),
            enabled = boolean(obj, "enabled", true),
            groupName = string(obj, "groupName", ""),
        )
        require(container.kindUrls.isBlank() || container.kindTitles.isNotBlank()) {
            "kindUrls必须与kindTitles同时提供;单分类请使用kindUrl"
        }
        val kindMode = mode(obj)
        if (kindMode != McpExploreKindMode.SELECTED) requireNoSelection(container)
        return McpExploreInput(container, kindMode)
    }

    private fun mode(obj: JsonObject): McpExploreKindMode {
        val dynamic = boolean(obj, "dynamic", false)
        val all = boolean(obj, "allKinds", false)
        require(!dynamic || !all) { "dynamic与allKinds不能同时为true" }
        return when {
            dynamic -> McpExploreKindMode.DYNAMIC
            all -> McpExploreKindMode.ALL
            else -> McpExploreKindMode.SELECTED
        }
    }

    private fun requireNoSelection(container: ExploreContainer) {
        require(container.kindTitle.isBlank() && container.kindUrl.isBlank() &&
            container.kindTitles.isBlank() && container.kindUrls.isBlank()) {
            "全部/动态分类模式不能同时指定分类"
        }
    }

    private fun requireBatchSize(size: Int) {
        require(size in 1..MAX_BATCH_SIZE) { "单批必须包含1至${MAX_BATCH_SIZE}个发现容器" }
    }

    private fun objectOf(element: JsonElement): JsonObject {
        require(element.isJsonObject) { "container必须为JSON对象" }
        return element.asJsonObject
    }

    private fun string(obj: JsonObject, key: String, default: String? = null): String {
        val value = obj.get(key) ?: return default ?: throw IllegalArgumentException("${key}不能为空")
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "${key}必须为字符串,不能为null" }
        return value.asString
    }

    private fun long(obj: JsonObject, key: String, default: Long): Long {
        val value = obj.get(key) ?: return default
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "${key}必须为整数" }
        return value.asString.toLongOrNull() ?: throw IllegalArgumentException("${key}必须为64位整数")
    }

    private fun integer(obj: JsonObject, key: String, default: Int): Int {
        val number = long(obj, key, default.toLong())
        require(number in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "${key}超出整数范围" }
        return number.toInt()
    }

    private fun boolean(obj: JsonObject, key: String, default: Boolean): Boolean {
        val value = obj.get(key) ?: return default
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "${key}必须为布尔值,不能为null" }
        return value.asBoolean
    }
}
