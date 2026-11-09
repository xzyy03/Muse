package com.muse.feature.agent.domain.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** 插件工具参数。type 取 string / integer / boolean，与 function-calling 的类型名一致。 */
data class PluginParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean
)

/**
 * 插件声明的一个工具。
 *
 * [command] 是在容器里执行的 shell 命令，支持 `{dir}` 占位符（插件目录绝对路径）。
 * 调用时参数以 JSON 放进环境变量 `MUSE_ARGS`，原始命令行另存 `MUSE_ARGS_RAW`；
 * stdout 即工具结果，stderr 与退出码非 0 时作为错误返回。
 */
data class PluginToolSpec(
    val name: String,
    val description: String,
    val command: String,
    val timeoutSeconds: Long,
    val parameters: List<PluginParameter>
)

/** `plugin.json` 解析结果。 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val tools: List<PluginToolSpec>
)

/** 磁盘上的一个插件：清单 + 目录 + README 正文（用户文档展示用）。 */
data class Plugin(
    val manifest: PluginManifest,
    val dir: String,
    val readme: String
) {
    val id: String get() = manifest.id
}

/**
 * `plugin.json` 解析器。
 *
 * 宽容解析：缺字段用默认值，单个工具写错只丢掉那一条，不让整个插件失效——
 * 手写清单出错时用户仍能在设置里看到插件并修正，而不是凭空消失。
 */
internal object PluginManifestParser {

    fun parse(json: String, fallbackId: String): PluginManifest? = runCatching {
        val root = Json.parseToJsonElement(json).jsonObject
        val id = root.string("id")?.takeIf { it.isNotBlank() } ?: fallbackId
        PluginManifest(
            id = id,
            name = root.string("name")?.takeIf { it.isNotBlank() } ?: id,
            version = root.string("version").orEmpty(),
            description = root.string("description").orEmpty(),
            author = root.string("author").orEmpty(),
            tools = root["tools"]?.jsonArray?.mapNotNull { parseTool(it.jsonObject) }.orEmpty()
        )
    }.getOrNull()

    private fun parseTool(el: JsonObject): PluginToolSpec? {
        val name = el.string("name")?.takeIf { it.isNotBlank() } ?: return null
        val command = el.string("command")?.takeIf { it.isNotBlank() } ?: return null
        return PluginToolSpec(
            name = name,
            description = el.string("description").orEmpty(),
            command = command,
            timeoutSeconds = el["timeout"]?.jsonPrimitive?.longOrNull?.coerceIn(1L, 1_800L) ?: 120L,
            parameters = el["parameters"]?.jsonArray?.mapNotNull { p ->
                val po = p.jsonObject
                val pn = po.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                PluginParameter(
                    name = pn,
                    type = po.string("type")?.lowercase()?.takeIf { it in PARAM_TYPES } ?: "string",
                    description = po.string("description").orEmpty(),
                    required = po["required"]?.jsonPrimitive?.booleanOrNull ?: true
                )
            }.orEmpty()
        )
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private val PARAM_TYPES = setOf("string", "integer", "boolean")
}
