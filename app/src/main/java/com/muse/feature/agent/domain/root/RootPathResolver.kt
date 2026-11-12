package com.muse.feature.agent.domain.root

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在 root 命名空间里解析路径。
 *
 * 必须交给 root 侧解析：App 进程看到的 `/sdcard` 是隔离视图，
 * `File.getCanonicalPath()` 的结果与 root shell 实际落点可能不同，
 * 用它来判断保护区会被符号链接绕过。
 */
@Singleton
class RootPathResolver @Inject constructor(
    private val suRunner: SuRunner
) {
    private companion object {
        const val RESOLVE_TIMEOUT_MS = 8_000L
        const val CACHE_LIMIT = 128
    }

    private val cache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) =
            size > CACHE_LIMIT
    }

    /** 解析失败返回 null（调用方按 DENY 处理，不放行）。 */
    suspend fun resolve(path: String): String? {
        val raw = path.trim()
        if (raw.isEmpty()) return null
        val absolute = if (raw.startsWith("/")) raw else "/$raw"

        synchronized(cache) { cache[absolute] }?.let { return it }

        resolveExact(absolute)?.let { resolved ->
            synchronized(cache) { cache[absolute] = resolved }
            return resolved
        }

        // 目标可能还不存在（准备新建）：解析父目录再拼回名字。
        val parent = absolute.substringBeforeLast('/', "")
        val name = absolute.substringAfterLast('/')
        if (parent.isEmpty() || name.isEmpty()) return null
        val resolvedParent = resolveExact(parent.ifEmpty { "/" }) ?: return null
        val combined = if (resolvedParent == "/") "/$name" else "$resolvedParent/$name"
        synchronized(cache) { cache[absolute] = combined }
        return combined
    }

    private suspend fun resolveExact(path: String): String? {
        val result = suRunner.run(listOf("readlink", "-f", "--", path), RESOLVE_TIMEOUT_MS)
        if (result.exitCode != 0) return null
        val out = result.output.lineSequence().firstOrNull()?.trim().orEmpty()
        if (out.isEmpty() || !out.startsWith("/")) return null
        return normalizeShared(out)
    }

    /**
     * 共享存储的 realpath 在不同设备/挂载方式下形态不同：
     * `/data/media/0`、`/mnt/user/0/primary`、`/mnt/runtime/default/emulated/0` 都是同一块。
     * 授权表以 `/storage/emulated/0` 为唯一形态记录，否则同一路径换个挂载点就被当成新路径，
     * 用户点过「始终允许」仍会反复弹窗。
     */
    private fun normalizeShared(path: String): String {
        val aliases = listOf(
            "/data/media/0",
            "/mnt/user/0/primary",
            "/mnt/runtime/default/emulated/0",
            "/mnt/runtime/read/emulated/0",
            "/mnt/runtime/write/emulated/0"
        )
        for (alias in aliases) {
            if (path == alias) return "/storage/emulated/0"
            if (path.startsWith("$alias/")) {
                return "/storage/emulated/0" + path.removePrefix(alias)
            }
        }
        return path
    }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }
}
