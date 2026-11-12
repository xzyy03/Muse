package com.muse.feature.agent.domain.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 单次 su 执行器。
 *
 * 每次调用起一个独立进程，执行完即退出，不常驻、不保持会话。
 * 串行化是为了避免多次 su 在部分设备的授权弹窗上互相踩踏。
 */
@Singleton
class SuRunner @Inject constructor() {

    private companion object {
        const val MAX_OUTPUT_CHARS = 256 * 1024
        const val PROBE_TIMEOUT_MS = 8_000L
    }

    private val mutex = Mutex()

    /** `su` 是否可用：能拿到 uid=0 才算就绪。 */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val result = run(listOf("id", "-u"), PROBE_TIMEOUT_MS)
            result.exitCode == 0 && result.output.trim() == "0"
        }.getOrDefault(false)
    }

    suspend fun run(argv: List<String>, timeoutMs: Long): RootExecResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (argv.isEmpty()) return@withContext RootExecResult("命令为空", -1)
            val invocation = listOf("su", "-c", argv.joinToString(" ") { RootTokenizer.quote(it) })
            var process: Process? = null
            try {
                process = ProcessBuilder(invocation).redirectErrorStream(true).start()
                val builder = StringBuilder()
                val reader = Thread {
                    runCatching {
                        process.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                if (builder.length < MAX_OUTPUT_CHARS) {
                                    builder.append(line).append('\n')
                                }
                            }
                        }
                    }
                }
                reader.start()
                val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                if (!finished) process.destroyForcibly()
                reader.join(2_000)
                RootExecResult(
                    output = builder.toString().trimEnd(),
                    exitCode = if (finished) process.exitValue() else -1,
                    timedOut = !finished
                )
            } catch (e: Exception) {
                RootExecResult(e.message ?: "执行失败", -1)
            } finally {
                process?.destroy()
            }
        }
    }
}
