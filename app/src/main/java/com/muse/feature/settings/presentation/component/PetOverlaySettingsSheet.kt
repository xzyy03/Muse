package com.muse.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muse.R
import com.muse.core.theme.Spacing
import com.muse.core.ui.AdaptiveModalBottomSheet
import com.muse.feature.pet.data.PetOverlaySettingsRepository
import androidx.compose.ui.res.stringResource

/**
 * 鲸娘悬浮窗设置弹窗：开关 + 尺寸调节。
 *
 * 尺寸用 1%～100% 的百分比映射到 1dp～320dp，拖到最左侧即 1dp（几乎不可见），
 * 满足「最小 1dp」的要求。
 */
private const val FULL_RANGE_DP =
    PetOverlaySettingsRepository.MAX_SIZE_DP - PetOverlaySettingsRepository.MIN_SIZE_DP

/** 百分比 → dp。 */
private fun dpOf(percent: Float): Float =
    PetOverlaySettingsRepository.MIN_SIZE_DP + FULL_RANGE_DP * (percent / 100f)

/** dp → 百分比。 */
private fun percentOf(dp: Float): Float =
    ((dp - PetOverlaySettingsRepository.MIN_SIZE_DP) / FULL_RANGE_DP * 100f).coerceIn(0f, 100f)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PetOverlaySettingsSheet(
    enabled: Boolean,
    sizeDp: Float,
    onToggle: (Boolean) -> Unit,
    onSizeChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = stringResource(R.string.pet_overlay_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.pet_overlay_enabled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            // 百分比 ↔ dp 线性映射：1% ≈ 1dp，100% = 320dp。
            // 拖动时用本地状态渲染（否则每次 onValueChange 都写 DataStore → 悬浮窗反复重排、滑块顿挫），
            // 松手才落盘。
            var dragPercent by remember(sizeDp) { mutableFloatStateOf(percentOf(sizeDp)) }

            Text(
                text = "${stringResource(R.string.pet_overlay_size)}  ${dpOf(dragPercent).toInt()}dp",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = dragPercent,
                onValueChange = { v -> dragPercent = v },
                onValueChangeFinished = { onSizeChange(dpOf(dragPercent)) },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "最小 1dp（约 1%），最大 ${PetOverlaySettingsRepository.MAX_SIZE_DP.toInt()}dp",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "悬浮图标可拖到任意位置，位置会记住。",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
