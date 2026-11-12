package com.muse.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muse.feature.settings.presentation.RootAccessState

/**
 * Root 权限面板：显示 su 是否可用，并管理「始终允许」记录。
 *
 * 撤销后，同一路径同一操作会重新弹窗——授权表只存 realpath + 操作，不存目录前缀，
 * 所以不会出现「一次允许整片目录」的放大效应。
 */
@Composable
internal fun RootAccessDialog(
    state: RootAccessState,
    onRefresh: () -> Unit,
    onRevoke: (String) -> Unit,
    onClearAll: () -> Unit,
    onResetFuse: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Root 权限") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = when {
                        state.checking -> "正在检测 su…"
                        state.available -> "su 可用，AI 可以通过 Root 工具执行单条命令。"
                        else -> "未检测到可用的 su。设备需要已 root 并授予本应用 su 权限。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!state.checking && !state.available) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    text = "始终允许的路径",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                if (state.grants.isEmpty()) {
                    Text(
                        text = "暂无记录。写共享目录时选择「始终允许」会出现在这里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.grants.forEach { grant ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = grant.realPath,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = grant.operation,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { onRevoke(grant.id) }) {
                                Text("撤销", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.grants.isNotEmpty()) {
                    TextButton(onClick = onClearAll) { Text("清空全部") }
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRefresh) { Text("重新检测") }
                TextButton(onClick = onResetFuse) { Text("解除暂停") }
            }
        }
    )
}
