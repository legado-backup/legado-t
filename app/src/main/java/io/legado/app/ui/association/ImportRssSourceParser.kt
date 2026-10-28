package io.legado.app.ui.association

import io.legado.app.data.entities.RssSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject

internal sealed interface RssSourceImportData {
    data class Sources(val items: List<RssSource>) : RssSourceImportData
    data class SourceUrls(val urls: List<String>) : RssSourceImportData
}

internal object ImportRssSourceParser {

    fun parse(json: String): RssSourceImportData {
        return when {
            json.isJsonArray() -> {
                val items = GSON.fromJsonArray<RssSource>(json).getOrThrow()
                validateSources(items)
                RssSourceImportData.Sources(items)
            }

            json.isJsonObject() -> {
                val root = GSON.fromJsonObject<Map<String, Any?>>(json).getOrThrow()
                if (root.containsKey("sourceUrls")) {
                    val urls = (root["sourceUrls"] as? List<*>)
                        ?.map { it as? String ?: throw NoStackTraceException("sourceUrls 格式不正确") }
                        ?: throw NoStackTraceException("sourceUrls 格式不正确")
                    RssSourceImportData.SourceUrls(urls)
                } else {
                    val source = GSON.fromJsonObject<RssSource>(json).getOrThrow()
                    validateSources(listOf(source))
                    RssSourceImportData.Sources(listOf(source))
                }
            }

            else -> throw NoStackTraceException("订阅源 JSON 格式不正确")
        }
    }

    private fun validateSources(items: List<RssSource>) {
        if (items.isNotEmpty() && items.first().sourceUrl.isEmpty()) {
            throw NoStackTraceException("不是订阅源")
        }
    }
}
