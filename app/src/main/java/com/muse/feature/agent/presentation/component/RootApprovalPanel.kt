package com.muse.feature.agent.presentation.component

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muse.feature.agent.domain.root.PendingRootApproval
import com.muse.feature.agent.domain.root.RootApprovalChoice

/**
 * Root 操作确认弹窗。
 *
 * 展示的是 realpath 之后的路径与 token 化后的 argv，不是模型写的描述——
 * 用户要看到真正会执行什么、落在哪里。
 */
@Composable
internal fun RootApprovalPanel(
    request: PendingRootApproval,
    onChoose: (RootApprovalChoice) -> Unit
) {
    if (request.isConsentRequest) {
        RootConsentPanel(request = request, onChoose = onChoose)
        return
    }

    AlertDialog(
        onDismissRequest = { onChoose(RootApprovalChoice.DENY) },
        title = { Text("Root 操作确认") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = request.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "命令",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = request.argv.joinToString(" "),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                )
                if (request.targets.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "目标路径",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    request.targets.forEach { target ->
                        Text(
                            text = "${target.op.label}  ${target.realPath}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                request.grantScope?.let { scope ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "选择「始终允许」后，该目录下的写入与删除都不再询问：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = scope,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onChoose(RootApprovalChoice.ONCE) }) {
                    Text("仅本次")
                }
                TextButton(onClick = { onChoose(RootApprovalChoice.SESSION) }) {
                    Text("本次会话")
                }
                TextButton(onClick = { onChoose(RootApprovalChoice.ALWAYS) }) {
                    Text(
                        text = if (request.grantScope != null) "始终允许此目录" else "始终允许",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(RootApprovalChoice.DENY) }) {
                Text("拒绝", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

/**
 * root 使用风险告知。只出现一次：同意后所有 root 调用直接执行，拒绝则关闭 root 通道。
 *
 * 这里展示的不是具体命令，而是「要不要开这个开关」——所以只给拒绝与同意两个出口。
 */
@Composable
private fun RootConsentPanel(
    request: PendingRootApproval,
    onChoose: (RootApprovalChoice) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onChoose(RootApprovalChoice.DENY) },
        title = { Text("允许使用 root 权限？") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Muse 将以 root 身份执行命令，用于访问普通权限到不了的目录。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "· 同意后，读写系统目录与共享存储都不再逐条询问\n" +
                        "· 系统关键目录（/system、/vendor、/boot 等）的写入仍会被拦截\n" +
                        "· root 权限可让任意程序修改或删除设备数据，误操作可能导致系统无法启动\n" +
                        "· 可随时在「软件权限 → Root 权限」中关闭",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "本次请求的操作：",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = request.argv.joinToString(" "),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onChoose(RootApprovalChoice.ALWAYS) },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("同意并允许", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(RootApprovalChoice.DENY) }) {
                Text("拒绝")
            }
        }
    )
}
