package com.muse.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muse.core.theme.Spacing
import com.muse.feature.agent.domain.plugin.Plugin
import androidx.compose.material3.OutlinedTextField
import compose.icons.feathericons.Download
import com.muse.feature.agent.domain.plugin.MarketPlugin
import com.muse.feature.settings.presentation.PluginsUiState
import compose.icons.FeatherIcons
import compose.icons.feathericons.Package

/**
 * 插件页：列出 `~/.muse/plugins/` 下的插件，可逐个启停、查看声明了哪些工具。
 *
 * 插件是声明式的：宿主只读 `plugin.json` 并把工具注册进工具箱，
 * 工具执行时在容器里跑插件自己给的命令，不加载任何插件侧框架代码。
 */
@Composable
internal fun PluginsSection(
    state: PluginsUiState,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onLoadMarket: () -> Unit = {},
    onSearchMarket: (String) -> Unit = {},
    onInstallMarket: (MarketPlugin) -> Unit = {}
) {
    var detail by remember { mutableStateOf<Plugin?>(null) }
    var showMarket by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = "插件目录：${state.rootPath}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "每个插件是一个子目录，含 plugin.json 清单。启用的插件会把自己的工具注册给 AI，调用时在容器里执行清单指定的命令。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingsGroupHeader(text = "插件市场")
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Download,
                title = "浏览插件市场",
                subtitle = "搜索并一键安装，装完立即可用",
                onClick = {
                    showMarket = true
                    onLoadMarket()
                }
            )
        }

        SettingsGroupHeader(text = "已安装")
        if (state.plugins.isEmpty()) {
            SettingsGroup {
                SettingsRow(
                    title = "暂无插件",
                    subtitle = "在插件目录下新建子目录并放入 plugin.json 即可",
                    enabled = false
                )
            }
        } else {
            SettingsGroup {
                state.plugins.forEachIndexed { index, plugin ->
                    if (index > 0) SettingsDivider()
                    val enabled = plugin.id !in state.disabledIds
                    SettingsRow(
                        icon = FeatherIcons.Package,
                        title = plugin.manifest.name,
                        subtitle = buildString {
                            if (plugin.manifest.version.isNotBlank()) append("v${plugin.manifest.version} · ")
                            append("${plugin.manifest.tools.size} 个工具")
                            if (plugin.manifest.description.isNotBlank()) {
                                append(" · ${plugin.manifest.description}")
                            }
                        },
                        onClick = { detail = plugin },
                        trailing = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = { onToggle(plugin.id, it) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    )
                }
            }
        }
    }

    if (showMarket) {
        MarketDialog(
            state = state,
            installedIds = remember(state.plugins) { state.plugins.map { it.id }.toSet() },
            onDismiss = { showMarket = false },
            onSearch = onSearchMarket,
            onInstall = onInstallMarket,
            onReload = onLoadMarket
        )
    }

    detail?.let { plugin ->
        PluginDetailDialog(
            plugin = plugin,
            onDismiss = { detail = null },
            onDelete = {
                onDelete(plugin.id)
                detail = null
            }
        )
    }
}

/** 插件详情：说明、工具清单、目录，以及删除入口。 */
@Composable
private fun PluginDetailDialog(
    plugin: Plugin,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(plugin.manifest.name) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (plugin.manifest.description.isNotBlank()) {
                    Text(plugin.manifest.description, style = MaterialTheme.typography.bodyMedium)
                }
                val meta = buildList {
                    if (plugin.manifest.version.isNotBlank()) add("版本 ${plugin.manifest.version}")
                    if (plugin.manifest.author.isNotBlank()) add("作者 ${plugin.manifest.author}")
                    add("ID ${plugin.manifest.id}")
                }
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = plugin.dir,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))
                Text("工具", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                if (plugin.manifest.tools.isEmpty()) {
                    Text(
                        text = "没有声明工具",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    plugin.manifest.tools.forEach { tool ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(tool.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            if (tool.description.isNotBlank()) {
                                Text(
                                    text = tool.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (tool.parameters.isNotEmpty()) {
                                Text(
                                    text = "参数：" + tool.parameters.joinToString("、") {
                                        it.name + if (it.required) "" else "(可选)"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (plugin.readme.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("说明", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        text = plugin.readme.take(2_000),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            TextButton(onClick = { confirmDelete = true }) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除插件") },
            text = { Text("将删除 ${plugin.dir} 及其全部文件，不可恢复。") },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }

/** 市场面板：搜索、浏览、一键安装。 */
@Composable
private fun MarketDialog(
    state: PluginsUiState,
    installedIds: Set<String>,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onInstall: (MarketPlugin) -> Unit,
    onReload: () -> Unit
) {
    val results = remember(state.market, state.marketQuery) {
        val q = state.marketQuery.trim().lowercase()
        if (q.isEmpty()) state.market else state.market.filter {
            it.name.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.author.lowercase().contains(q) ||
                it.tags.any { tag -> tag.lowercase().contains(q) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("插件市场") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.marketQuery,
                    onValueChange = onSearch,
                    singleLine = true,
                    placeholder = { Text("搜索插件名称、说明或标签") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                when {
                    state.marketLoading -> Text(
                        "正在拉取索引…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.marketError != null -> Column {
                        Text(
                            text = state.marketError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = onReload) { Text("重试") }
                    }
                    results.isEmpty() -> Text(
                        if (state.marketQuery.isBlank()) "市场暂无插件" else "没有匹配的插件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    results.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name + if (item.version.isNotBlank()) "  v${item.version}" else "",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                if (item.description.isNotBlank()) {
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val meta = buildList {
                                    if (item.author.isNotBlank()) add(item.author)
                                    if (item.tags.isNotEmpty()) add(item.tags.joinToString(" / "))
                                }
                                if (meta.isNotEmpty()) {
                                    Text(
                                        text = meta.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            when {
                                state.installingId == item.id -> Text(
                                    "安装中…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                item.id in installedIds -> Text(
                                    "已安装",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                else -> TextButton(
                                    onClick = { onInstall(item) },
                                    enabled = state.installingId == null
                                ) { Text("安装") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        dismissButton = {
            TextButton(onClick = onReload) { Text("刷新") }
        }
    )
}
