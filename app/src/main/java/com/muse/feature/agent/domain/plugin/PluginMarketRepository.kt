package com.muse.feature.agent.domain.plugin

import com.muse.core.util.FileLogger
import com.muse.feature.agent.domain.container.ContainerInstaller
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** 插件市场里的一条索引项。 */
data class MarketPlugin(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val tags: List<String>,
    val zipUrl: String
)

/**
 * 插件市场：拉取索引、搜索、下载安装。
 *
 * 索引是一个 JSON：
 * ```json
 * {"plugins":[{"id":"wordcount","name":"字数统计","version":"1.0.0",
 *   "description":"…","author":"Muse","tags":["文本"],
 *   "zip":"https://…/wordcount.zip"}]}
 * ```
 * 与 dsh-go 的市场一样，App 只读声明、只下载插件包，不执行任何索引侧代码。
 */
@Singleton
class PluginMarketRepository @Inject constructor(
    private val containerInstaller: ContainerInstaller,
    private val client: OkHttpClient
) {
    private companion object {
        const val TAG = "PluginMarket"

        /**
         * 索引地址候选：GitHub raw 优先，其余为镜像，任一可用即可。
         * 国内直连 raw 常失败，镜像顺序决定了市场页的可用性。
         */
        val INDEX_URLS = listOf(
            "https://raw.githubusercontent.com/xzyy03/Muse/main/plugins/index.json",
            "https://cdn.jsdelivr.net/gh/xzyy03/Muse@main/plugins/index.json",
            "https://gh-proxy.com/https://raw.githubusercontent.com/xzyy03/Muse/main/plugins/index.json"
        )

        const val MAX_ZIP_BYTES = 20L * 1024 * 1024
    }

    private val pluginsRoot: File get() = File(containerInstaller.museDir, "plugins")

    /** 拉取并解析索引；全部节点失败时返回错误信息。 */
    suspend fun loadIndex(): Result<List<MarketPlugin>> = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (url in INDEX_URLS) {
            val body = runCatching { httpGetText(url) }.onFailure { lastError = it }.getOrNull()
            if (body.isNullOrBlank()) continue
            val parsed = runCatching { parseIndex(body) }.getOrNull()
            if (parsed != null) {
                FileLogger.i(TAG, "索引拉取成功：${parsed.size} 个插件（$url）")
                return@withContext Result.success(parsed)
            }
        }
        Result.failure(lastError ?: IllegalStateException("插件索引不可用，请检查网络"))
    }

    /** 按名称、描述、标签、作者做大小写不敏感的子串过滤；空查询返回全部。 */
    fun search(all: List<MarketPlugin>, query: String): List<MarketPlugin> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter {
            it.name.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.author.lowercase().contains(q) ||
                it.tags.any { tag -> tag.lowercase().contains(q) }
        }
    }

    /** 下载 zip 并解压安装到 `~/.muse/plugins/<id>/`；已存在时覆盖同名文件。 */
    suspend fun install(plugin: MarketPlugin): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = httpGetBytes(plugin.zipUrl)
            require(bytes.isNotEmpty()) { "下载内容为空" }
            require(bytes.size <= MAX_ZIP_BYTES) { "插件包过大" }
            val destination = File(pluginsRoot, plugin.id)
            destination.mkdirs()
            unzip(bytes, destination)
            if (!File(destination, "plugin.json").isFile) {
                // 包内可能多套了一层目录：把唯一的子目录内容上提到插件根。
                val only = destination.listFiles()?.singleOrNull()
                if (only != null && only.isDirectory && File(only, "plugin.json").isFile) {
                    only.listFiles()?.forEach { child -> child.renameTo(File(destination, child.name)) }
                    only.deleteRecursively()
                }
            }
            FileLogger.i(TAG, "已安装插件 ${plugin.id} 到 ${destination.absolutePath}")
        }
    }

    private fun httpGetText(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "muse-android").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string() ?: error("空响应")
        }
    }

    private fun httpGetBytes(url: String): ByteArray {
        val request = Request.Builder().url(url).header("User-Agent", "muse-android").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.bytes() ?: error("空响应")
        }
    }

    /** 解压并防止路径穿越（zip slip）：只允许落在目标目录内。 */
    private fun unzip(bytes: ByteArray, destination: File) {
        val rootPath = destination.canonicalPath + File.separator
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = File(destination, entry.name).canonicalFile
                require(target.path.startsWith(rootPath)) { "插件包含非法路径：${entry.name}" }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out -> zip.copyTo(out) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun parseIndex(body: String): List<MarketPlugin> {
        val root = Json.parseToJsonElement(body).jsonObject
        return root["plugins"]?.jsonArray?.mapNotNull { el ->
            val o = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val zip = o["zip"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MarketPlugin(
                id = id,
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: id,
                version = o["version"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                description = o["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                author = o["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                tags = o["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                zipUrl = zip
            )
        }.orEmpty()
    }
}
