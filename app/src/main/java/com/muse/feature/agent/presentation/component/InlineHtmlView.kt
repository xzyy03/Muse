package com.muse.feature.agent.presentation.component

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muse.core.theme.Radius
import com.muse.core.theme.semanticColors
import compose.icons.FeatherIcons
import compose.icons.feathericons.Code
import compose.icons.feathericons.Eye
import compose.icons.feathericons.Maximize2

/** 内联预览高度：超出在框内滚动，不撑爆整条消息。 */
private val InlineHtmlHeight = 420.dp

/**
 * 聊天内联 HTML 渲染。
 *
 * JS 开启（多数页面依赖它），但关闭本地文件访问与 file:// 跨域读取——
 * 模型生成的页面不该有能力读设备上的任意文件。
 */
@Composable
internal fun InlineHtmlView(
    html: String,
    modifier: Modifier = Modifier,
    showSourceToggle: Boolean = true,
    onFullscreen: (() -> Unit)? = null
) {
    var showSource by remember(html) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showSourceToggle || onFullscreen != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showSourceToggle) {
                    TextButton(onClick = { showSource = !showSource }) {
                        Icon(
                            imageVector = if (showSource) FeatherIcons.Eye else FeatherIcons.Code,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (showSource) "预览" else "源码",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (onFullscreen != null) {
                    IconButton(onClick = onFullscreen) {
                        Icon(
                            imageVector = FeatherIcons.Maximize2,
                            contentDescription = "全屏",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (showSource) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = InlineHtmlHeight)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(MaterialTheme.semanticColors.mutedSurface)
                    .padding(8.dp)
            ) {
                Text(
                    text = html,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(InlineHtmlHeight)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(Color.White)
            ) {
                HtmlWebView(html = html)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlWebView(html: String) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let { view ->
                view.loadUrl("about:blank")
                view.destroy()
            }
            webView = null
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AndroidColor.WHITE)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    allowFileAccess = false
                    allowContentAccess = false
                    setAllowFileAccessFromFileURLs(false)
                    setAllowUniversalAccessFromFileURLs(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    builtInZoomControls = false
                    displayZoomControls = false
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean = true
                }
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                webView = this
            }
        },
        update = { view ->
            view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    )
}

/** 全屏 HTML 预览。 */
@Composable
internal fun FullscreenHtmlDialog(
    html: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            HtmlWebView(html = html)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text("关闭", color = Color.Black, fontSize = 14.sp)
            }
        }
    }
}
