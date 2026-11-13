package com.muse.feature.agent.presentation.component

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.muse.R
import com.muse.core.theme.Radius
import compose.icons.FeatherIcons
import compose.icons.feathericons.Maximize2

/**
 * 聊天内联视频播放器。
 *
 * 播放器用 TextureView 布局加载：默认的 SurfaceView 在 Compose 滚动容器里
 * 会被合成层画成黑块，且点击事件到不了控件。
 * 每个播放器独占一个 ExoPlayer，离开组合即释放——列表里可能同时存在多条视频。
 */
@Composable
internal fun InlineVideoPlayer(
    mediaUri: Uri,
    modifier: Modifier = Modifier,
    onFullscreen: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var errorText by remember(mediaUri) { mutableStateOf<String?>(null) }
    val player = remember(mediaUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(mediaUri))
            playWhenReady = false
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // 把真实原因显示出来：黑屏时看不出是取不到文件还是解码失败。
                errorText = error.errorCodeName + " · " + (error.cause?.message ?: error.message)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                val view = android.view.LayoutInflater.from(ctx)
                    .inflate(R.layout.view_inline_video, null) as PlayerView
                view.player = player
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                view.setShowNextButton(false)
                view.setShowPreviousButton(false)
                view
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )
        errorText?.let { message ->
            Text(
                text = "无法播放：" + message,
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(12.dp)
            )
        }
        if (onFullscreen != null) {
            IconButton(
                onClick = onFullscreen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
            ) {
                Icon(
                    imageVector = FeatherIcons.Maximize2,
                    contentDescription = "全屏播放",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** 全屏播放。 */
@Composable
internal fun FullscreenVideoDialog(
    mediaUri: Uri,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(mediaUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(mediaUri))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    val view = android.view.LayoutInflater.from(ctx)
                        .inflate(R.layout.view_inline_video, null) as PlayerView
                    view.player = player
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    view
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Text("关闭", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}
