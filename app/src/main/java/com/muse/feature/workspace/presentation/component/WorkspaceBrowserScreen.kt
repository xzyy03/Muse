package com.muse.feature.workspace.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import com.muse.core.ui.AdaptiveModalBottomSheet
import compose.icons.feathericons.Edit2
import compose.icons.feathericons.Trash2
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import com.muse.R
import com.muse.core.theme.Spacing
import com.muse.feature.workspace.presentation.WorkspaceBrowserViewModel
import com.muse.feature.workspace.presentation.WorkspaceEntry
import com.muse.feature.workspace.presentation.WorkspaceExternalOpener
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.CornerUpLeft
import compose.icons.feathericons.ExternalLink
import compose.icons.feathericons.File
import compose.icons.feathericons.Folder
import compose.icons.feathericons.FolderPlus
import compose.icons.feathericons.FilePlus
import compose.icons.feathericons.RefreshCw
import compose.icons.feathericons.Search
import java.io.File

/**
 * 工作区文件浏览页面：从当前会话的工作区根逐层进入目录。
 *
 * 顶部栏提供返回、上一级、用外部管理器打开，以及在当前目录新建文件/文件夹；
 * 长按条目弹出重命名/删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceBrowserScreen(
    sessionId: String,
    rootPath: String?,
    onNavigateBack: () -> Unit,
    viewModel: WorkspaceBrowserViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(sessionId, rootPath) { viewModel.open(sessionId, rootPath) }

    LaunchedEffect(message) {
        message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    var createFolderDialog by remember { mutableStateOf(false) }
    var createFileDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var actionTarget by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var siblingProjects by remember { mutableStateOf<List<WorkspaceEntry>>(emptyList()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (state.selectingProject) "选择项目"
                            else state.currentPath.substringAfterLast('/').ifBlank { "工作区" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (state.selectingProject) "选定后本对话长按直达此项目"
                            else state.currentPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!state.selectingProject) {
                        IconButton(onClick = viewModel::changeProject) {
                            Icon(FeatherIcons.Folder, contentDescription = "绑定项目")
                        }
                        IconButton(onClick = { showProjectPicker = true }) {
                            Icon(FeatherIcons.Search, contentDescription = "浏览其它项目")
                        }
                    }
                    IconButton(
                        onClick = viewModel::goUp,
                        enabled = state.canGoUp
                    ) {
                        Icon(
                            FeatherIcons.CornerUpLeft,
                            contentDescription = "上一级",
                            tint = if (state.canGoUp) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(FeatherIcons.RefreshCw, contentDescription = "刷新")
                    }
                    IconButton(
                        onClick = {
                            val ok = WorkspaceExternalOpener.openInMtManager(
                                context = context,
                                dir = viewModel.hostDir(state.currentPath)
                            )
                            if (!ok) {
                                android.widget.Toast.makeText(
                                    context,
                                    "未检测到 MT 管理器",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) {
                        Icon(FeatherIcons.ExternalLink, contentDescription = "用 MT 管理器打开")
                    }
                    if (!state.readOnly) {
                        IconButton(onClick = { createFolderDialog = true }) {
                            Icon(FeatherIcons.FolderPlus, contentDescription = "新建文件夹")
                        }
                        IconButton(onClick = { createFileDialog = true }) {
                            Icon(FeatherIcons.FilePlus, contentDescription = "新建文件")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.loading && state.entries.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.error != null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                state.entries.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "这个文件夹是空的",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = Spacing.md,
                        vertical = Spacing.sm
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    items(state.entries, key = { it.path }) { entry ->
                        WorkspaceEntryRow(
                            entry = entry,
                            onClick = {
                                when {
                                    // 选项目阶段：点一下即把这个目录定为本对话的项目。
                                    state.selectingProject -> viewModel.selectProject(entry)
                                    entry.isDirectory -> viewModel.enter(entry.path)
                                    else -> Unit
                                }
                            },
                            onLongClick = {
                                if (!state.selectingProject && !state.readOnly) actionTarget = entry
                            }
                        )
                    }
                }
            }
        }
    }

    if (createFolderDialog) {
        NameInputDialog(
            title = "新建文件夹",
            initial = "",
            onConfirm = {
                viewModel.createFolder(it)
                createFolderDialog = false
            },
            onDismiss = { createFolderDialog = false }
        )
    }

    if (createFileDialog) {
        NameInputDialog(
            title = "新建文件",
            initial = "",
            onConfirm = {
                viewModel.createFile(it)
                createFileDialog = false
            },
            onDismiss = { createFileDialog = false }
        )
    }

    renameTarget?.let { target ->
        NameInputDialog(
            title = "重命名",
            initial = target.name,
            onConfirm = { newName ->
                viewModel.rename(target, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    LaunchedEffect(showProjectPicker) {
        if (showProjectPicker) siblingProjects = viewModel.listSiblingProjects()
    }

    if (showProjectPicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showProjectPicker = false },
            title = { Text("浏览其它项目") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    items(siblingProjects, key = { it.path }) { project ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onClick = {
                                        showProjectPicker = false
                                        viewModel.browseOther(project.path)
                                    },
                                    onLongClick = {}
                                )
                                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                FeatherIcons.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProjectPicker = false }) { Text("取消") }
            }
        )
    }

    actionTarget?.let { target ->
        WorkspaceEntryActionSheet(
            entry = target,
            onRename = {
                actionTarget = null
                renameTarget = target
            },
            onDelete = {
                actionTarget = null
                deleteTarget = target
            },
            onDismiss = { actionTarget = null }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除") },
            text = { Text("确定删除「${target.name}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target)
                        deleteTarget = null
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

/** 长按条目弹出的操作菜单：与侧边栏文件树同样的底部弹层风格。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceEntryActionSheet(
    entry: WorkspaceEntry,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.md)
            )
            BrowserSheetAction(FeatherIcons.Edit2, "重命名", MaterialTheme.colorScheme.onSurface, onRename)
            BrowserSheetAction(FeatherIcons.Trash2, "删除", MaterialTheme.colorScheme.error, onDelete)
        }
    }
}

@Composable
private fun BrowserSheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
            Spacer(Modifier.width(Spacing.lg))
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
        }
    }
}

@Composable
private fun WorkspaceEntryRow(
    entry: WorkspaceEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (entry.isDirectory) FeatherIcons.Folder else FeatherIcons.File,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (entry.isDirectory) {
            Icon(
                FeatherIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun NameInputDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("名称") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
