package com.muse.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muse.feature.agent.domain.root.RootConsent
import com.muse.feature.settings.presentation.RootAccessState

/**
 * 软件权限页的 Root 面板：只有一个总开关。
 *
 * 逐路径授权已经取消——用户同意一次就代表接受 root 风险，之后不再按目录反复询问。
 * 这里保留的入口是「关掉 / 重新打开」。
 */
@Composable
internal fun RootAccessDialog(
    state: RootAccessState,
    onRefresh: () -> Unit,
    onGrantConsent: () -> Unit,
    onRevokeConsent: () -> Unit,
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
                    text = "使用授权",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = when (state.consent) {
                        RootConsent.GRANTED -> "已允许。root 命令直接执行，读写系统目录与共享存储都不再询问。"
                        RootConsent.DENIED -> "已关闭。root 调用会被拒绝，AI 无法使用 Root 工具。"
                        RootConsent.UNKNOWN -> "未授权。第一次用到 root 时会弹一次风险告知。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (state.consent) {
                        RootConsent.GRANTED -> MaterialTheme.colorScheme.primary
                        RootConsent.DENIED -> MaterialTheme.colorScheme.error
                        RootConsent.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = "同意后可以读写系统目录与共享存储；系统关键目录（/system、/vendor、/boot 等）的写入仍会被拦截。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.consent != RootConsent.DENIED) {
                    TextButton(onClick = onRevokeConsent) {
                        Text("关闭权限", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (state.consent != RootConsent.GRANTED) {
                    TextButton(onClick = onGrantConsent) {
                        Text("允许使用", fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
        dismissButton = {
            TextButton(onClick = onRefresh) { Text("重新检测") }
        }
    )
}
