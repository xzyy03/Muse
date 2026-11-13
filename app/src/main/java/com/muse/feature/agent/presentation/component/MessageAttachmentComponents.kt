package com.muse.feature.agent.presentation.component

import com.muse.feature.agent.presentation.isVideo
import com.muse.feature.agent.presentation.isHtml
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.muse.R
import com.muse.core.theme.Brand
import com.muse.core.theme.Radius
import com.muse.core.theme.Spacing
import com.muse.core.ui.LocalImageViewer
import com.muse.core.ui.ThumbnailCache
import com.muse.core.util.ApkInstaller
import com.muse.feature.agent.presentation.AgentAttachment
import com.muse.feature.workspace.domain.FileAccessProvider
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Image
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 附件缩略图边长：一行一个文件时左侧的小图，够看清是截图还是照片即可。 */
private val AttachmentThumbSize = 44.dp

/**
 * 已发送 / 已生成附件的列表：**一行一个文件**。
 *
 * 每行是「左侧缩略图（图片放真缩略图，其余按类型给图标）+ 文件名 + 大小 · 容器路径」，
 * 整行可点。不再用横向滚动的方形卡片带：76dp 的格子里文件名必被截断，多个文件还得左右滑，
 * 一行一个才能一眼看清发的是哪几份。
 *
 * 点击语义：图片一律走内置全屏查看器（隐式依赖组合树上层提供 [LocalImageViewer]，聊天区都满足）；
 * 非图片用调用方给的 [onClick] —— 工具卡片传的是「系统 app 打开」，助手气泡不传即不可点。
 */
@Composable
internal fun MessageAttachmentList(
    attachments: List<AgentAttachment>,
    onClick: ((AgentAttachment) -> Unit)? = null,
    /** 用户自己发出的附件：只给缩略图行，不就地展开播放器与页面渲染。 */
    compact: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        attachments.forEach { attachment ->
            when {
                compact -> MessageAttachmentRow(attachment = attachment, onClick = onClick)
                attachment.isVideo -> VideoClip(attachment)
                attachment.isHtml -> HtmlClip(attachment)
                else -> MessageAttachmentRow(attachment = attachment, onClick = onClick)
            }
        }
    }
}

@Composable
private fun MessageAttachmentRow(
    attachment: AgentAttachment,
    onClick: ((AgentAttachment) -> Unit)? = null
) {
    val viewer = LocalImageViewer.current
    val handler: (() -> Unit)? = if (attachment.isImage) {
        { viewer.show(attachment.toViewerRequest()) }
    } else if (onClick != null) {
        { onClick(attachment) }
    } else {
        null
    }
    val fallbackName = stringResource(R.string.common_file)
    val name = attachment.fileName.ifBlank { fallbackName }
    // 读屏与长按提示用：图片是「预览」，其它是「打开」，两者落到的地方不一样。
    val actionLabel = if (attachment.isImage) {
        stringResource(R.string.chat_attachment_preview, name)
    } else {
        stringResource(R.string.chat_attachment_open, name)
    }
    // 次要信息：大小 + 容器路径。路径对「AI 发过来的文件」是关键信息（能直接在终端/编辑器里找到），
    // 整行放不下时靠省略号截断，不换行。
    val meta = remember(attachment.sizeBytes, attachment.containerPath) {
        listOf(formatBytes(attachment.sizeBytes), attachment.containerPath)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = chatMutedSurfaceColor(),
        modifier = Modifier
            .fillMaxWidth()
            // 先裁圆角再挂 clickable：涟漪才会被裁在圆角内，不会溢出成直角方块
            .clip(RoundedCornerShape(Radius.md))
            .then(
                if (handler != null) {
                    Modifier.clickable(onClickLabel = actionLabel, onClick = handler)
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            AttachmentThumb(attachment = attachment)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 有动作才给指示箭头：不可点的行不该长得像能点（如助手气泡里的非图片附件）
            if (handler != null) {
                Icon(
                    FeatherIcons.ChevronRight,
                    contentDescription = null,
                    tint = Brand.IconGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachmentThumb(attachment: AgentAttachment) {
    Surface(
        shape = RoundedCornerShape(Radius.sm),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(AttachmentThumbSize)
    ) {
        if (attachment.isImage) {
            AttachmentImageThumb(attachment = attachment)
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    FeatherIcons.FileText,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachmentImageThumb(attachment: AgentAttachment) {
    // 读盘 + 解码放 IO 线程：以前在 remember 里同步 decodeFile，滚到带图消息时会卡主线程。
    // 采样与 OOM 兜底统一走 decodeSampledBitmap。
    val path = attachment.localPath
    val bitmap by produceState<ImageBitmap?>(ThumbnailCache.peek(path), path) {
        value = ThumbnailCache.peek(path) ?: withContext(Dispatchers.IO) { ThumbnailCache.load(path) }
    }
    val loaded = bitmap
    if (loaded != null) {
        ComposeImage(
            bitmap = loaded,
            contentDescription = attachment.fileName.ifBlank { stringResource(R.string.common_image_preview) },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                FeatherIcons.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 打开已发送的非图片附件（用系统 app）。默认空实现，与 `LocalImageViewer` 同一套路 ——
 * 卡片渲染在 `ToolMessageBody` 这类中间层，用 CompositionLocal 避免一路加回调参数、
 * 免去中间层为转发 lambda 改签名。
 */
fun interface AttachmentOpener {
    fun open(attachment: AgentAttachment)
}

internal val LocalAttachmentOpener = staticCompositionLocalOf<AttachmentOpener> { AttachmentOpener { } }

/**
 * 用系统对应 app 打开附件文件（FileProvider 授权 URI）。
 *
 * 卡片记录的 [AgentAttachment.localPath] 不保证仍然有效：远程 SSH 模式下它是 `copyToLocal` 的临时副本
 * （进程重启或系统清缓存后消失），外部本地目录工作区、自定义挂载源的文件又落在 FileProvider 声明的目录树之外。
 * 因此失效时退回 [AgentAttachment.containerPath] 经 [fileAccess] 重取，重取到的路径仍无法授权时
 * 再复制进 cacheDir（已由 cache-path 覆盖）分享。文件确实不存在与最终无法分享分别提示，不再混用一句文案。
 */
internal suspend fun openSentAttachment(
    context: Context,
    attachment: AgentAttachment,
    fileAccess: FileAccessProvider
) {
    val local = withContext(Dispatchers.IO) { resolveLocalFile(attachment, fileAccess) }
    if (local == null) {
        Toast.makeText(context, context.getString(R.string.chat_open_file_missing), Toast.LENGTH_SHORT).show()
        return
    }
    // APK 安装包：统一走 ApkInstaller（含「允许安装未知应用」的授权判定与设置页引导）。
    // 外部本地目录工作区、自定义挂载源的文件不在 FileProvider 目录树内，先拷进 cacheDir 再授权。
    if (isApk(attachment)) {
        val installable = withContext(Dispatchers.IO) {
            if (isShareable(context, local)) local else copyToShareCache(context, local)
        }
        if (installable == null) {
            Toast.makeText(context, context.getString(R.string.chat_open_file_unshareable), Toast.LENGTH_SHORT).show()
            return
        }
        when (ApkInstaller.install(context, installable)) {
            ApkInstaller.Result.STARTED -> Unit
            ApkInstaller.Result.NEED_PERMISSION -> {
                Toast.makeText(context, context.getString(R.string.chat_open_apk_permission), Toast.LENGTH_LONG).show()
                if (!ApkInstaller.openInstallPermissionSettings(context)) {
                    Toast.makeText(context, context.getString(R.string.chat_open_file_no_app), Toast.LENGTH_SHORT).show()
                }
            }
            ApkInstaller.Result.NO_HANDLER ->
                Toast.makeText(context, context.getString(R.string.chat_open_file_no_app), Toast.LENGTH_SHORT).show()
        }
        return
    }
    val shareable = withContext(Dispatchers.IO) {
        if (isShareable(context, local)) local else copyToShareCache(context, local)
    }
    val uri = shareable?.let {
        runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }.getOrNull()
    }
    if (uri == null) {
        Toast.makeText(context, context.getString(R.string.chat_open_file_unshareable), Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, attachment.mimeType.ifBlank { "*/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.chat_open_file_no_app), Toast.LENGTH_SHORT).show()
    }
}

/** 卡片记录的宿主路径失效（远程临时副本被清、文件被删或移动）时，退回容器路径重取。 */
private suspend fun resolveLocalFile(attachment: AgentAttachment, fileAccess: FileAccessProvider): File? {
    attachment.localPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }?.let { return it }
    val containerPath = attachment.containerPath
    if (containerPath.isBlank()) return null
    return runCatching { fileAccess.copyToLocal(containerPath) }.getOrNull()?.takeIf { it.isFile }
}

/** 路径落在 FileProvider 声明的目录树内时可直接授权；不能就抛异常，故按能否取到 URI 判定。 */
private fun isShareable(context: Context, file: File): Boolean =
    runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }.isSuccess

/**
 * 把无法直接授权的文件（外部本地目录工作区、自定义挂载源、SD 卡等）复制进 cacheDir 再分享。
 * 目标目录按源路径散列分隔：既避开同名文件互相覆盖，也不把散列值挂进分享出去的文件名。
 */
private fun copyToShareCache(context: Context, source: File): File? = runCatching {
    val dir = File(File(context.cacheDir, SHARE_CACHE_DIR), source.absolutePath.hashCode().toString())
    dir.mkdirs()
    val target = File(dir, source.name.ifBlank { "file" })
    source.copyTo(target, overwrite = true)
    target
}.getOrNull()

private const val SHARE_CACHE_DIR = "sent_files"

private fun isApk(attachment: AgentAttachment): Boolean {
    if (attachment.mimeType.equals("application/vnd.android.package-archive", ignoreCase = true)) return true
    val name = attachment.fileName.lowercase()
    return name.endsWith(".apk") || name.endsWith(".apks") || name.endsWith(".xapk")
}

/** 附件读取用的 FileAccessProvider：聊天区由上层提供，缺省时内联渲染降级成文件行。 */
internal val LocalAttachmentFileAccess = staticCompositionLocalOf<FileAccessProvider?> { null }

/**
 * 视频附件：直接内联播放。
 *
 * 先把字节落到应用私有 cache 再播放：容器模式的路径经过 PRoot rootfs 映射，
 * ExoPlayer 在 App 进程里按原串打开会指向不存在的文件；而 content:// 又需要额外的
 * 读取授权链，部分机型直接黑屏。落到真实存在的私有文件最稳。
 */
@Composable
private fun VideoClip(attachment: AgentAttachment) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fileAccess = LocalAttachmentFileAccess.current
    var fullscreenUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var preparing by remember(attachment.localPath) { mutableStateOf(true) }
    var loadError by remember(attachment.localPath) { mutableStateOf<String?>(null) }

    val mediaUri by produceState<android.net.Uri?>(
        initialValue = null,
        attachment.localPath,
        attachment.containerPath,
        fileAccess
    ) {
        value = if (fileAccess == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "media-preview").apply { mkdirs() }
                val stamp = attachment.sizeBytes.toString() + "-" + attachment.fileName.hashCode()
                val target = File(dir, stamp + "-" + attachment.fileName.ifBlank { "clip.mp4" })
                if (!target.isFile || target.length() == 0L) {
                    // localPath 是 sendFile 落库时的宿主路径，历史消息里可能为空或已失效；
                    // 容器路径始终有效，按它读能覆盖这两种情况。
                    val bytes = runCatching { fileAccess.readBytes(attachment.localPath) }
                        .getOrNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?: fileAccess.readBytes(attachment.containerPath)
                    require(bytes.isNotEmpty()) { "视频为空" }
                    target.writeBytes(bytes)
                }
                android.net.Uri.fromFile(target)
            }.onFailure { loadError = it.message ?: it.javaClass.simpleName }.getOrNull()
        }
        preparing = false
    }

    val shown = mediaUri
    if (shown == null) {
        // 还在准备 → 占位；准备失败 → 说明原因。直接回退成文件行会让人以为功能没生效。
        if (preparing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(chatMutedSurfaceColor()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "正在准备视频…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            MessageAttachmentRow(attachment = attachment, onClick = null)
            Text(
                text = "无法读取视频文件：${loadError ?: "未知原因"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = Spacing.sm, top = 2.dp)
            )
        }
    } else {
        InlineVideoPlayer(
            mediaUri = shown,
            modifier = Modifier.padding(top = Spacing.xs),
            onFullscreen = { fullscreenUri = shown }
        )
    }

    fullscreenUri?.let { target ->
        FullscreenVideoDialog(mediaUri = target, onDismiss = { fullscreenUri = null })
    }
}

/** HTML 附件：内联 WebView 渲染，可切源码、可全屏。 */
@Composable
private fun HtmlClip(attachment: AgentAttachment) {
    val fileAccess = LocalAttachmentFileAccess.current
    var fullscreen by remember { mutableStateOf(false) }

    val html by produceState<String?>(
        initialValue = null,
        attachment.containerPath,
        fileAccess
    ) {
        value = if (fileAccess == null) null else withContext(Dispatchers.IO) {
            runCatching {
                fileAccess.readFile(attachment.containerPath)
            }.getOrElse {
                // 容器路径读不到时退回宿主本地路径（远程模式的历史消息可能只剩本地副本）。
                runCatching {
                    fileAccess.copyToLocal(attachment.localPath).readText()
                }.getOrNull()
            }
        }
    }

    val content = html
    if (content == null) {
        MessageAttachmentRow(attachment = attachment, onClick = null)
    } else {
        InlineHtmlView(
            html = content,
            modifier = Modifier.padding(top = Spacing.xs),
            onFullscreen = { fullscreen = true }
        )
    }

    if (fullscreen && content != null) {
        FullscreenHtmlDialog(html = content, onDismiss = { fullscreen = false })
    }
}
