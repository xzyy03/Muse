package com.muse.feature.workspace.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import java.io.File

/**
 * 把工作区目录交给 MT 管理器打开。
 *
 * MT 管理器接受 `content://` 形式的外部存储/文档 URI 作为起始路径，
 * 因此优先把宿主目录转成 [FileProvider] 授权 URI；拿不到时退回本应用的
 * [com.muse.feature.workspace.data.provider.WorkspaceDocumentsProvider] 文档 URI。
 */
object WorkspaceExternalOpener {

    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    private const val DOCUMENTS_PROVIDER_SUFFIX = ".documents"

    /** MT 管理器的包名候选：正式版与共存版。 */
    private val MT_PACKAGES = listOf(
        "bin.mt.plus",
        "bin.mt.plus.canary"
    )

    private const val MT_ACTIVITY = "bin.mt.plus.Main"

    /**
     * 用 MT 管理器打开 [dir]。返回 false 表示未安装 MT 管理器。
     */
    fun openInMtManager(context: Context, dir: File): Boolean {
        if (!dir.exists()) return false
        val uri = buildDirectoryUri(context, dir) ?: return false
        val installed = MT_PACKAGES.firstOrNull { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        } ?: return false

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClassName(installed, MT_ACTIVITY)
            setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            // 显式 Activity 名在部分版本不存在时，退回按包名派发。
            runCatching {
                val fallback = Intent(Intent.ACTION_VIEW).apply {
                    setPackage(installed)
                    setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true
            }.getOrDefault(false)
        }
    }

    /** 优先 FileProvider（覆盖 filesDir / cacheDir / 外部存储），否则用本应用 DocumentsProvider。 */
    private fun buildDirectoryUri(context: Context, dir: File): Uri? {
        runCatching {
            FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, dir)
        }.getOrNull()?.let { return it }

        val base = File(context.filesDir, "projects").absolutePath
        val target = dir.absolutePath
        if (!target.startsWith(base)) return null
        return runCatching {
            DocumentsContract.buildDocumentUri(
                context.packageName + DOCUMENTS_PROVIDER_SUFFIX,
                target
            )
        }.getOrNull()
    }
}
