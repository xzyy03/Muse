package com.muse.feature.agent.domain.plugin

import com.muse.core.util.FileLogger
import com.muse.feature.agent.domain.container.ContainerInstaller
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray

/**
 * 插件仓库：扫描 `~/.muse/plugins/` 下的插件目录并读写启停配置。
 *
 * 目录约定：
 * ```
 * ~/.muse/plugins/<id>/
 * ├── plugin.json   清单（必需）
 * ├── README.md     说明（可选，详情页展示）
 * └── ...           脚本等任意文件
 * ```
 *
 * 启停只记「禁用名单」，存在 `~/.muse/plugins.json`，格式与技能一致
 * （`{"disabled": ["id"]}`），外部手工编辑即时生效。
 */
@Singleton
class PluginRepository @Inject constructor(
    private val containerInstaller: ContainerInstaller
) {
    private companion object {
        const val TAG = "PluginRepository"
        const val PLUGIN_DIR = "plugins"
        const val MANIFEST_FILE = "plugin.json"
        const val README_FILE = "README.md"
        const val CONFIG_FILE = "plugins.json"
        const val DISABLED_KEY = "disabled"
    }

    private val pluginsRoot: File get() = File(containerInstaller.museDir, PLUGIN_DIR)
    private val configFile: File get() = File(containerInstaller.museDir, CONFIG_FILE)

    fun pluginsRootPath(): String = pluginsRoot.absolutePath

    /** 扫描所有插件目录；清单缺失或解析失败的目录直接跳过。 */
    fun list(): List<Plugin> {
        val root = pluginsRoot
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?.mapNotNull { readPlugin(it) }
            .orEmpty()
    }

    private fun readPlugin(dir: File): Plugin? {
        val manifestFile = File(dir, MANIFEST_FILE)
        if (!manifestFile.isFile) return null
        val raw = runCatching { manifestFile.readText() }.getOrNull() ?: return null
        val manifest = PluginManifestParser.parse(raw, dir.name) ?: return runCatching {
            FileLogger.w(TAG, "plugin.json 解析失败: ${manifestFile.path}")
        }.let { null }
        val readme = File(dir, README_FILE).takeIf { it.isFile }
            ?.let { runCatching { it.readText() }.getOrNull() }
            .orEmpty()
        return Plugin(manifest, dir.absolutePath, readme)
    }

    /** 被禁用的插件 id 集合。 */
    fun disabledIds(): Set<String> {
        val file = configFile
        if (!file.isFile) return emptySet()
        return runCatching {
            Json.parseToJsonElement(file.readText()).jsonObject[DISABLED_KEY]
                ?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?.toSet()
                .orEmpty()
        }.getOrDefault(emptySet())
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val current = disabledIds().toMutableSet()
        if (enabled) current.remove(id) else current.add(id)
        writeConfig(current)
    }

    private fun writeConfig(disabled: Set<String>) {
        runCatching {
            configFile.parentFile?.mkdirs()
            val payload = buildJsonObject {
                putJsonArray(DISABLED_KEY) { disabled.sorted().forEach { add(it) } }
            }
            configFile.writeText(JsonObject(payload).toString())
        }.onFailure { FileLogger.w(TAG, "写入插件配置失败: ${it.message}", it) }
    }

    /** 删除插件目录（含全部文件）。 */
    fun delete(id: String): Boolean {
        val dir = File(pluginsRoot, id)
        if (!dir.isDirectory) return false
        val removed = runCatching { dir.deleteRecursively() }.getOrDefault(false)
        if (removed) {
            setEnabled(id, true)
            FileLogger.i(TAG, "已删除插件 $id")
        }
        return removed
    }
}
