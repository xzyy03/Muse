package com.muse.feature.settings.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muse.core.theme.Spacing
import com.muse.feature.agent.presentation.component.LocalChatBubbleStyle
import com.muse.feature.agent.presentation.component.UserMessageBubble
import com.muse.feature.settings.data.repository.ChatBubbleStyle
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check

/**
 * 聊天气泡样式选择弹窗：两列平铺，每格带实时预览。
 *
 * 预览用与真实消息区同一个 [UserMessageBubble]，因此看到的效果与实际聊天完全一致；
 * 气泡统一靠左排列，配合等高格子让 8 个选项排成整齐的矩阵。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatBubbleStyleSheet(
    selected: ChatBubbleStyle,
    onSelect: (ChatBubbleStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "聊天气泡",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "选择你喜欢的消息气泡样式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val styles = ChatBubbleStyle.entries
            styles.chunked(2).forEach { rowStyles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    rowStyles.forEach { style ->
                        ChatBubbleStyleCell(
                            style = style,
                            selected = style == selected,
                            onClick = { onSelect(style) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // 奇数个时补一个等宽占位，保证最后一行左对齐不拉伸。
                    if (rowStyles.size == 1) {
                        Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleStyleCell(
    style: ChatBubbleStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        // 预览区：固定等高，气泡一律靠左，8 个格子拼起来才整齐。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            CompositionLocalProvider(LocalChatBubbleStyle provides style) {
                UserMessageBubble(
                    text = styleSampleText(style),
                    maxWidth = 160.dp
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = style.displayName,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            if (selected) {
                Icon(
                    imageVector = FeatherIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/** 各样式用同一句短示例，便于横向比较形状与配色差异。 */
private fun styleSampleText(style: ChatBubbleStyle): String = when (style) {
    ChatBubbleStyle.CLASSIC -> "气泡样式"
    ChatBubbleStyle.SHADOW -> "柔和投影"
    ChatBubbleStyle.MINIMAL -> "极简纯白"
    ChatBubbleStyle.DARK -> "暗夜模式"
    ChatBubbleStyle.SQUARE -> "漫画方格"
    ChatBubbleStyle.GREEN -> "清新绿"
    ChatBubbleStyle.VALENTINE -> "心动时刻"
    ChatBubbleStyle.ORIGINAL -> "原版风格"
}
