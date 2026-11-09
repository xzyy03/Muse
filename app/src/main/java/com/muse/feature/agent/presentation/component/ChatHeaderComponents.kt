package com.muse.feature.agent.presentation.component

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import compose.icons.feathericons.MoreHorizontal
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.muse.R
import com.muse.core.theme.Radius
import com.muse.core.theme.Spacing
import com.muse.feature.onboarding.domain.OnboardingStep
import com.muse.feature.onboarding.presentation.onboardingTarget
import com.muse.feature.settings.presentation.component.ModelLogoIcon
import compose.icons.FeatherIcons
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.Globe
import compose.icons.feathericons.Menu
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Terminal
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun ChatHeader(
    sessionTitle: String,
    modelName: String?,
    inputTokens: Int,
    outputTokens: Int,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToGit: () -> Unit,
    onNavigateToBrowser: () -> Unit = {},
    connectionState: com.muse.feature.agent.domain.container.ConnectionState? = null,
    showMenuButton: Boolean = true,
    terminalActive: Boolean = false,
    gitActive: Boolean = false,
    browserActive: Boolean = false,
    /** 当前供应商名，显示在模型后的括号里。 */
    providerName: String? = null,
    /** 点击标题重命名会话。 */
    onRenameSession: (() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 大屏常驻侧栏时隐掉汉堡键：侧栏已经摆在左边，再给个开关反而困惑。
                if (showMenuButton) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier
                            .size(40.dp)
                            .onboardingTarget(OnboardingStep.OPEN_SIDEBAR)
                    ) {
                        Icon(
                            FeatherIcons.Menu,
                            contentDescription = stringResource(R.string.chat_open_sidebar),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(Spacing.sm))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (showMenuButton) 2.dp else 0.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    // 标题：点击进入重命名
                    Text(
                        text = sessionTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.1).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onRenameSession != null) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onRenameSession
                            )
                        } else Modifier
                    )
                    // 副行：模型图标 + 模型名 +（供应商），小号灰字，紧贴标题下方
                    val subtitle = buildString {
                        if (!modelName.isNullOrBlank()) append(modelName)
                        if (!providerName.isNullOrBlank()) {
                            if (isNotEmpty()) append(" ")
                            append("(")
                            append(providerName)
                            append(")")
                        }
                        if (isEmpty()) append(stringResource(R.string.chat_no_model_selected))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(top = 0.dp)
                    ) {
                        if (!modelName.isNullOrBlank()) {
                            ModelLogoIcon(modelName = modelName, size = 11.dp)
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                lineHeight = 13.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                MemoryChip()
                // 新建 / Git / 终端 收进三点菜单，避免顶栏露太多图标。
                OverlayMenuButton(
                    terminalActive = terminalActive,
                    gitActive = gitActive,
                    onNewChat = onNewChat,
                    onNavigateToTerminal = onNavigateToTerminal,
                    onNavigateToGit = onNavigateToGit
                )
            }
            // 远程模式：左边 SSH 连接状态，右边 token 累计统计
            if (connectionState != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ConnectionIndicator(state = connectionState)
                    TokenStats(
                        inputTokens = inputTokens,
                        outputTokens = outputTokens
                    )
                }
            }
        }
    }
}

/** 顶栏工作台入口按钮：大屏右栏开着对应内容时高亮，否则看不出点一下是开还是关。 */
@Composable
private fun WorkbenchIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 顶栏三点菜单：把「新建对话 / Git / 终端」收进一个下拉菜单。
 *
 * 顶栏宽度有限，三个图标并排会把标题挤得很窄；收进菜单后标题能拿到完整宽度。
 */
@Composable
private fun OverlayMenuButton(
    terminalActive: Boolean,
    gitActive: Boolean,
    onNewChat: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToGit: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
            Icon(
                FeatherIcons.MoreHorizontal,
                contentDescription = stringResource(R.string.chat_more_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        // 自绘弹出菜单：不用 DropdownMenu，因为它强制带 MenuDefaults 的柔和阴影，
        // 这里需要完全无阴影的平面卡片。
        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(with(density) { (-8).dp.roundToPx() }, with(density) { 42.dp.roundToPx() }),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.width(168.dp).padding(vertical = 4.dp)) {
                        HeaderMenuRow(
                            icon = FeatherIcons.Plus,
                            label = stringResource(R.string.chat_new_session),
                            active = false,
                            onClick = { expanded = false; onNewChat() }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 40.dp, end = 12.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        HeaderMenuRow(
                            icon = FeatherIcons.GitBranch,
                            label = stringResource(R.string.chat_open_git),
                            active = gitActive,
                            onClick = { expanded = false; onNavigateToGit() }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 40.dp, end = 12.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        HeaderMenuRow(
                            icon = FeatherIcons.Terminal,
                            label = stringResource(R.string.chat_open_terminal),
                            active = terminalActive,
                            onClick = { expanded = false; onNavigateToTerminal() }
                        )
                    }
                }
            }
        }
    }
}

/** 菜单项：图标 + 文本，开启中时右侧蓝点。 */
@Composable
private fun HeaderMenuRow(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(11.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (active) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

@Composable
private fun ConnectionIndicator(
    state: com.muse.feature.agent.domain.container.ConnectionState
) {
    val (dotColor, text) = when (state) {
        com.muse.feature.agent.domain.container.ConnectionState.CONNECTED ->
            MaterialTheme.colorScheme.primary to stringResource(R.string.chat_ssh_connected)
        com.muse.feature.agent.domain.container.ConnectionState.CONNECTING ->
            MaterialTheme.colorScheme.tertiary to stringResource(R.string.chat_ssh_connecting)
        com.muse.feature.agent.domain.container.ConnectionState.FAILED ->
            MaterialTheme.colorScheme.error to stringResource(R.string.chat_ssh_failed)
        com.muse.feature.agent.domain.container.ConnectionState.DISCONNECTED ->
            MaterialTheme.colorScheme.outline to stringResource(R.string.chat_ssh_disconnected)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TokenStats(inputTokens: Int, outputTokens: Int) {
    val inStr = formatTokenCount(inputTokens.toLong())
    val outStr = formatTokenCount(outputTokens.toLong())
    Text(
        text = "↑$inStr ↓$outStr",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun RemoteConnectingPlaceholder(
    state: com.muse.feature.agent.domain.container.ConnectionState
) {
    val text = when (state) {
        com.muse.feature.agent.domain.container.ConnectionState.CONNECTING -> stringResource(R.string.chat_connecting_remote)
        com.muse.feature.agent.domain.container.ConnectionState.FAILED -> stringResource(R.string.chat_remote_connect_failed)
        com.muse.feature.agent.domain.container.ConnectionState.DISCONNECTED -> stringResource(R.string.chat_no_remote_connection)
        com.muse.feature.agent.domain.container.ConnectionState.CONNECTED -> ""
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (state == com.muse.feature.agent.domain.container.ConnectionState.CONNECTING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun WelcomeState(bottomReserve: Dp, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier.padding(Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        val targetOffset = minOf(-(maxHeight * 0.13f), -(bottomReserve / 2))
        val animatedOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = if (Build.VERSION.SDK_INT >= 30) snap() else tween(durationMillis = 220),
            label = "welcome-offset"
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.offset(y = animatedOffset)
        ) {
            Text(
                text = stringResource(R.string.chat_placeholder),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.chat_input_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class DeviceMemoryStat(
    val usedBytes: Long,
    val totalBytes: Long,
    val availBytes: Long
) {
    val percent: Int
        get() = if (totalBytes <= 0L) 0 else ((usedBytes * 100) / totalBytes).toInt()
}

private fun readDeviceMemory(context: Context): DeviceMemoryStat {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val info = ActivityManager.MemoryInfo()
    manager?.getMemoryInfo(info)
    val total = info.totalMem
    val avail = info.availMem
    return DeviceMemoryStat(
        usedBytes = (total - avail).coerceAtLeast(0L),
        totalBytes = total,
        availBytes = avail
    )
}

private fun formatGb(bytes: Long): String =
    String.format(Locale.US, "%.1f", bytes / 1024.0 / 1024.0 / 1024.0)

/** 顶栏内存胶囊：显示占用百分比，点开显示已用 / 总 / 可用。 */
@Composable
private fun MemoryChip() {
    val context = LocalContext.current
    val density = LocalDensity.current
    var stat by remember { mutableStateOf(readDeviceMemory(context)) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            stat = readDeviceMemory(context)
            delay(3_000)
        }
    }

    Box {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { expanded = true }
        ) {
            Text(
                text = stringResource(R.string.memory_chip_label, stat.percent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, with(density) { 40.dp.roundToPx() }),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.memory_popup_used,
                                formatGb(stat.usedBytes),
                                formatGb(stat.totalBytes)
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.memory_popup_avail, formatGb(stat.availBytes)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
