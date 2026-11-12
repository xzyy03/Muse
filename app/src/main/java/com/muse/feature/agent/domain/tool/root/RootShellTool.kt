package com.muse.feature.agent.domain.tool.root

import com.muse.core.util.FileLogger
import com.muse.feature.agent.domain.model.AgentContext
import com.muse.feature.agent.domain.root.RootCommandGateway
import com.muse.feature.agent.domain.tool.AbstractContextualTool
import com.muse.feature.agent.domain.tool.ParameterType
import com.muse.feature.agent.domain.tool.ToolCapability
import com.muse.feature.agent.domain.tool.ToolParameter
import com.muse.feature.agent.domain.tool.ToolPermissionPolicy
import com.muse.feature.agent.domain.tool.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject

/**
 * 通过 su 在宿主 Android 上执行单条命令。
 *
 * 与 `Bash`（容器内）和 `Shizuku`（shell 身份）都不同：本工具是真正的 root，
 * 因此所有调用都经过 [RootCommandGateway] 的策略检查，写共享目录还要用户确认。
 * 只接受单条命令，不接受管道、重定向、命令串联——需要多步时分成多次调用。
 */
class RootShellTool @Inject constructor(
    private val gateway: RootCommandGateway
) : AbstractContextualTool() {

    private companion object {
        const val TAG = "RootShellTool"
        const val DEFAULT_TIMEOUT_SECONDS = 120L
        const val MAX_TIMEOUT_SECONDS = 1_800L
    }

    override val name = "Root"

    override val description =
        "以 root 身份在宿主 Android 上执行单条命令。只读命令（ls/cat/getprop/ps 等）直接执行；" +
            "写共享目录 /storage/emulated/0 会弹窗要求用户确认；/ 与 /system、/vendor、/boot 等系统区" +
            "只允许读取，任何写入都会被拒绝。不支持管道、重定向、&& 串联与命令替换，需要多步请拆成多次调用。"

    override val permissionPolicy = ToolPermissionPolicy.ASK
    override val capabilities = setOf(ToolCapability.EXECUTE_COMMANDS)

    override val parameters: Map<String, ToolParameter> = mapOf(
        "command" to ToolParameter(
            name = "command",
            type = ParameterType.STRING,
            description = "要执行的单条命令，例如 ls -l /system 或 getprop ro.product.model",
            required = true
        ),
        "timeout" to ToolParameter(
            name = "timeout",
            type = ParameterType.INTEGER,
            description = "超时秒数，默认 $DEFAULT_TIMEOUT_SECONDS，上限 $MAX_TIMEOUT_SECONDS",
            required = false
        )
    )

    private fun timeoutMs(args: Map<String, JsonElement>): Long {
        val seconds = args["timeout"]?.jsonPrimitive?.longOrNull ?: DEFAULT_TIMEOUT_SECONDS
        return seconds.coerceIn(1L, MAX_TIMEOUT_SECONDS) * 1000L
    }

    override suspend fun executeWithContext(
        args: Map<String, JsonElement>,
        context: AgentContext
    ): ToolResult {
        val command = args["command"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("缺少必需参数：command")

        return try {
            val sessionId = context.sessionId.orEmpty()
            val result = gateway.execute(command, sessionId, timeoutMs(args))
            result.fold(
                onSuccess = { exec ->
                    if (exec.exitCode != 0 && exec.output.isBlank()) {
                        ToolResult.Error("命令以退出码 ${exec.exitCode} 结束，无输出")
                    } else {
                        ToolResult.Success(JsonPrimitive(exec.output.ifBlank { "（无输出）" }))
                    }
                },
                onFailure = { error ->
                    FileLogger.w(TAG, "root 执行被拒或失败: $command -> ${error.message}")
                    ToolResult.Error(error.message ?: "root 执行失败", code = "ROOT_DENIED")
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.e(TAG, "root 执行异常: $command", e)
            ToolResult.Error("root 执行失败：${e.message}", code = "ROOT_FAILED")
        }
    }
}
