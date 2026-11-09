package com.muse.feature.agent.domain.plugin

import com.muse.feature.agent.domain.container.CommandEngine
import com.muse.feature.agent.domain.container.CommandEvent
import com.muse.feature.agent.domain.tool.AgentTool
import com.muse.feature.agent.domain.tool.ParameterType
import com.muse.feature.agent.domain.tool.ToolCapability
import com.muse.feature.agent.domain.tool.ToolParameter
import com.muse.feature.agent.domain.tool.ToolResult
import com.muse.feature.workspace.data.repository.WorkspaceRepository
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 由插件清单声明出来的工具。
 *
 * 宿主只负责把参数拼成环境变量、在容器里跑清单给的命令，再原样返回 stdout——
 * 与 dsh-go 的「App 不执行插件代码、只消费插件产出的声明」思路一致：
 * 插件的能力边界完全由它自己的脚本决定，宿主不加载任何插件侧框架代码。
 *
 * 调用约定（插件脚本读这些环境变量）：
 * - `MUSE_ARGS`：参数 JSON，如 `{"city":"上海"}`
 * - `MUSE_PLUGIN_ID` / `MUSE_PLUGIN_DIR`：插件 id 与目录绝对路径
 * - `MUSE_WORKSPACE`：当前工作区路径（无工作区时为空串）
 */
class PluginTool(
    private val spec: PluginToolSpec,
    private val pluginId: String,
    private val pluginDir: String,
    private val commandEngine: CommandEngine,
    private val workspaceRepository: WorkspaceRepository
) : AgentTool() {

    override val name: String = spec.name

    override val description: String =
        spec.description.ifBlank { "插件 $pluginId 提供的工具" }

    override val capabilities: Set<ToolCapability> = setOf(ToolCapability.EXECUTE_COMMANDS)

    override val parameters: Map<String, ToolParameter> = spec.parameters.associate { p ->
        p.name to ToolParameter(
            name = p.name,
            type = when (p.type) {
                "integer" -> ParameterType.INTEGER
                "boolean" -> ParameterType.BOOLEAN
                else -> ParameterType.STRING
            },
            description = p.description.ifBlank { p.name },
            required = p.required
        )
    }

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val workspace = runCatching { workspaceRepository.currentPath() }.getOrDefault("")
        val command = buildCommand(JsonObject(args).toString(), workspace)
        val lines = mutableListOf<String>()
        var exitCode: Int? = null
        val events = commandEngine.runCommandStream(
            command = command,
            projectPath = workspace.takeIf { it.isNotBlank() },
            timeoutMs = spec.timeoutSeconds * 1_000L
        ).toList()
        events.forEach { event ->
            when (event) {
                is CommandEvent.Line -> lines += event.text
                is CommandEvent.Exit -> exitCode = event.code
            }
        }
        val output = lines.joinToString("\n").trim()
        return if (exitCode == null || exitCode == 0) {
            ToolResult.Success(JsonPrimitive(output.ifBlank { "（无输出）" }))
        } else {
            ToolResult.Error(
                message = "插件 $pluginId 的工具 $name 执行失败（退出码 $exitCode）" +
                    if (output.isNotBlank()) "：\n$output" else "",
                code = "PLUGIN_FAILED"
            )
        }
    }

    /** 组装执行命令：先导出环境变量，再跑清单里的命令；{dir} 替换为插件目录。 */
    private fun buildCommand(argsJson: String, workspace: String): String {
        val cmd = spec.command.replace("{dir}", shellQuote(pluginDir))
        return buildString {
            append("MUSE_PLUGIN_ID=").append(shellQuote(pluginId)).append(' ')
            append("MUSE_PLUGIN_DIR=").append(shellQuote(pluginDir)).append(' ')
            append("MUSE_WORKSPACE=").append(shellQuote(workspace)).append(' ')
            append("MUSE_ARGS=").append(shellQuote(argsJson)).append(' ')
            append("sh -c ").append(shellQuote(cmd))
        }
    }

    private fun shellQuote(raw: String): String = "'" + raw.replace("'", "'\\''") + "'"
}
