package com.muse.feature.settings.presentation.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 头像裁剪：拖动 + 双指缩放，方框内的内容就是最终头像。
 *
 * 不引第三方裁剪库：需要的只是「按方框取一块再缩放」，自己算比拉一个库回来更省依赖。
 */
@Composable
internal fun AvatarCropDialog(
    source: ByteArray,
    onCancel: () -> Unit,
    onConfirm: (ByteArray) -> Unit
) {
    val bitmap = remember(source) {
        runCatching { BitmapFactory.decodeByteArray(source, 0, source.size) }.getOrNull()
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSizePx by remember { mutableStateOf(0) }

    if (bitmap == null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("无法读取图片") },
            text = { Text("这张图片无法解码，换一张试试。") },
            confirmButton = { TextButton(onClick = onCancel) { Text("知道了") } }
        )
        return
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "调整头像",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "拖动与缩放，圆框内就是最终头像",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .onSizeChanged { boxSizePx = it.width }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (boxSizePx == 0) return@detectTransformGestures
                            val base = min(boxSizePx.toFloat() / bitmap.width, boxSizePx.toFloat() / bitmap.height)
                            val newScale = (scale * zoom).coerceIn(1f, 6f)
                            // 图片缩放后的实际尺寸，限制平移量使画面始终填满方框。
                            val scaledW = bitmap.width * base * newScale
                            val scaledH = bitmap.height * base * newScale
                            val maxX = ((scaledW - boxSizePx) / 2f).coerceAtLeast(0f)
                            val maxY = ((scaledH - boxSizePx) / 2f).coerceAtLeast(0f)
                            offset = Offset(
                                x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                            )
                            scale = newScale
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
                // 圆形边框：让用户明确最终头像的圆形边界。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.5.dp, Color.White.copy(alpha = 0.75f), CircleShape)
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text("取消") }
                TextButton(
                    onClick = {
                        val cropped = renderCroppedAvatar(bitmap, boxSizePx, scale, offset)
                        if (cropped != null) onConfirm(cropped) else onCancel()
                    }
                ) {
                    Text("确定", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** 按方框可见区域裁一块并缩放到 512×512 的 PNG。 */
private fun renderCroppedAvatar(
    bitmap: Bitmap,
    boxSizePx: Int,
    scale: Float,
    offset: Offset
): ByteArray? = runCatching {
    if (boxSizePx <= 0) return null
    val base = min(boxSizePx.toFloat() / bitmap.width, boxSizePx.toFloat() / bitmap.height)
    val drawScale = base * scale
    // 方框中心对应原图上的点：先反推平移，再换算成原图坐标。
    val centerX = bitmap.width / 2f - offset.x / drawScale
    val centerY = bitmap.height / 2f - offset.y / drawScale
    val half = (boxSizePx / 2f) / drawScale
    val left = (centerX - half).coerceIn(0f, max(0f, bitmap.width - 2 * half))
    val top = (centerY - half).coerceIn(0f, max(0f, bitmap.height - 2 * half))
    val side = (half * 2).coerceAtMost(min(bitmap.width.toFloat(), bitmap.height.toFloat()))

    val src = Bitmap.createBitmap(
        bitmap,
        left.roundToInt().coerceIn(0, bitmap.width - 1),
        top.roundToInt().coerceIn(0, bitmap.height - 1),
        side.roundToInt().coerceIn(1, bitmap.width),
        side.roundToInt().coerceIn(1, bitmap.height)
    )
    val size = 512
    val out = Bitmap.createScaledBitmap(src, size, size, true)
    ByteArrayOutputStream().use { stream ->
        out.compress(Bitmap.CompressFormat.PNG, 100, stream)
        out.recycle()
        if (src !== bitmap) src.recycle()
        stream.toByteArray()
    }
}.getOrNull()
