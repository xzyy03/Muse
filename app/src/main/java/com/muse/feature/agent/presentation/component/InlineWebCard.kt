package com.muse.feature.agent.presentation.component

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muse.core.theme.Radius
import com.muse.core.theme.Spacing
import com.muse.core.theme.semanticColors
import compose.icons.FeatherIcons
import compose.icons.feathericons.Globe
import compose.icons.feathericons.Maximize2
import compose.icons.feathericons.X

/** 内嵌网页使用的 User-Agent：由聊天区按浏览器的当前预设提供，与内置浏览器保持一致。 */
internal val LocalBrowserUserAgent = staticCompositionLocalOf<String?> { null }

/** 内嵌网页高度：给够视频播放器可视面积，又不至于一条消息占满整屏。 */
private val InlineWebHeight = 260.dp

/**
 * 聊天内嵌网页：AI 用 browser 工具打开视频站点后，直接在这里播放。
 *
 * 用独立 WebView 而不复用 BrowserManager 的实例：那边的 WebView 同一时刻只能挂在一个容器下，
 * 聊天卡片和浏览器页会互相抢占。Cookie 由 CookieManager 全局共享，登录态可以延续。
 */
@Composable
internal fun InlineWebCard(
    url: String,
    title: String?,
    modifier: Modifier = Modifier,
    userAgent: String? = null
) {
    var fullscreen by remember(url) { mutableStateOf(false) }
    var loading by remember(url) { mutableStateOf(true) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(
                imageVector = FeatherIcons.Globe,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = title?.takeIf { it.isNotBlank() } ?: url,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { fullscreen = true }) {
                Icon(
                    imageVector = FeatherIcons.Maximize2,
                    contentDescription = "全屏",
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(InlineWebHeight)
                .clip(RoundedCornerShape(Radius.md))
                .background(MaterialTheme.semanticColors.mutedSurface)
        ) {
            InlineWebView(
                url = url,
                userAgent = userAgent,
                onLoadingChanged = { loading = it }
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }

    if (fullscreen) {
        FullscreenWebDialog(
            url = url,
            title = title,
            userAgent = userAgent,
            onDismiss = { fullscreen = false }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InlineWebView(
    url: String,
    userAgent: String?,
    onLoadingChanged: (Boolean) -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let { view ->
                view.loadUrl("about:blank")
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
            webView = null
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    // 视频站的播放键常由脚本触发，要求用户手势会让内嵌播放点不动
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    allowFileAccess = false
                    allowContentAccess = false
                    setAllowFileAccessFromFileURLs(false)
                    setAllowUniversalAccessFromFileURLs(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                }
                if (!userAgent.isNullOrBlank()) {
                    settings.userAgentString = userAgent
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        onLoadingChanged(true)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        onLoadingChanged(false)
                    }
                }
                loadUrl(url)
                webView = this
            }
        },
        update = { view ->
            if (view.url != url) view.loadUrl(url)
        }
    )
}

/** 全屏网页。 */
@Composable
internal fun FullscreenWebDialog(
    url: String,
    title: String?,
    userAgent: String?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title?.takeIf { it.isNotBlank() } ?: url,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = FeatherIcons.X,
                        contentDescription = "关闭",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                InlineWebView(
                    url = url,
                    userAgent = userAgent,
                    onLoadingChanged = {}
                )
            }
        }
    }
}
