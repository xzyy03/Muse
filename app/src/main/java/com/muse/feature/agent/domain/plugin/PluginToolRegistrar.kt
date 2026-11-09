package com.muse.feature.agent.domain.plugin

import com.muse.core.util.FileLogger
import com.muse.feature.agent.domain.container.CommandEngine
import com.muse.feature.agent.domain.tool.AgentTool
import com.muse.feature.agent.domain.tool.ToolRegistry
import com.muse.feature.workspace.data.repository.WorkspaceRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把启用的插件工具注册进 [ToolRegistry]。
 *
 * 与 MCP 的动态注册同一思路：插件工具在运行时进出工具箱，不进静态注册表。
 * 启动时与插件启停、删除后各调一次 [refresh]。
 */
@Singleton
class PluginToolRegistrar @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val pluginRepository: PluginRepository,
    private val commandEngine: CommandEngine,
    private val workspaceRepository: WorkspaceRepository
) {
    private companion object {
        const val TAG = "PluginToolRegistrar"
    }

    /** 本容器当前注册过的插件工具名，用于刷新时区分「插件注册的」与「内置的」。 */
    private val registered = linkedSetOf<String>()

    @Synchronized
    fun refresh() {
        val plugins = pluginRepository.list()
        val disabled = pluginRepository.disabledIds()
        val wanted = LinkedHashMap<String, AgentTool>()

        plugins.filter { it.id !in disabled }.forEach { plugin ->
            plugin.manifest.tools.forEach { spec ->
                val occupied = spec.name in toolRegistry.getToolNames() && spec.name !in registered
                if (occupied) {
                    // 与内置工具同名时不覆盖：插件不该悄悄顶掉 readFile / Bash 这类核心工具。
                    FileLogger.w(TAG, "插件 ${plugin.id} 的工具 ${spec.name} 与已有工具同名，已跳过")
                } else {
                    wanted[spec.name] = PluginTool(
                        spec = spec,
                        pluginId = plugin.id,
                        pluginDir = plugin.dir,
                        commandEngine = commandEngine,
                        workspaceRepository = workspaceRepository
                    )
                }
            }
        }

        registered.filterNot { it in wanted }.forEach { toolRegistry.unregister(it) }
        registered.clear()
        wanted.forEach { (name, tool) -> toolRegistry.register(name, tool) }
        registered.addAll(wanted.keys)

        FileLogger.i(TAG, "插件工具已刷新：${wanted.size} 个（插件 ${plugins.size} 个，禁用 ${disabled.size} 个）")
    }
}
