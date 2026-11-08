package com.muse.feature.settings.presentation.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.muse.R
import com.muse.core.theme.Spacing
import com.muse.core.ui.AppTextField
import com.muse.feature.agent.domain.prompt.PromptFragment
import com.muse.feature.agent.domain.prompt.PromptFragmentSource
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.Upload
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 提示词片段新建/编辑页：自带顶栏（返回 + 右上角保存），与 [SubAgentEditorScreen] 一致，
 * 不能嵌进设置页的 Scaffold。
 *
 * 编号即身份，决定片段在系统提示词里的位置；可改。作用域选项目或全局，决定覆盖写到哪一层。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PromptEditorScreen(
    isNew: Boolean,
    initialNumber: Int,
    initialTitle: String,
    initialDescription: String,
    initialContent: String,
    initialScope: PromptFragmentSource,
    hasWorkspace: Boolean,
    onSave: (number: Int, title: String, scope: PromptFragmentSource, content: String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var numberText by rememberSaveable { mutableStateOf(if (isNew) "" else "%02d".format(initialNumber)) }
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var content by rememberSaveable { mutableStateOf(initialContent) }
    var scopeIsProject by rememberSaveable { mutableStateOf(initialScope == PromptFragmentSource.PROJECT) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // 在 IO 线程读文件，大文件不卡 UI
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text != null) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    content = text
                    // 名称未填时用文件名（去掉扩展名）兜底
                    if (title.isBlank()) {
                        val name = queryDisplayName(context, uri)?.substringBeforeLast('.') ?: ""
                        if (name.isNotBlank()) title = name
                    }
                }
            }
        }
    }

    val number = numberText.toIntOrNull()
    val numberValid = number != null && number in 0..99
    val canSave = numberValid && title.isNotBlank()

    BackHandler { onNavigateBack() }

    Scaffold(
        containerColor = settingsPageBackground(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isNew) R.string.prompts_editor_new else R.string.prompts_editor_edit
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = settingsPageBackground(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            onSave(
                                number ?: 0,
                                title.trim(),
                                if (scopeIsProject) PromptFragmentSource.PROJECT else PromptFragmentSource.GLOBAL,
                                PromptFragment.composeContent(description, content)
                            )
                        }
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            SettingsGroupHeader(text = stringResource(R.string.prompts_field_basic))
            SettingsGroup {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    AppTextField(
                        value = numberText,
                        onValueChange = { new -> numberText = new.filter { it.isDigit() }.take(2) },
                        label = stringResource(R.string.prompts_field_number),
                        placeholder = stringResource(R.string.prompts_field_number_placeholder),
                        singleLine = true
                    )
                    AppTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = stringResource(R.string.prompts_field_title),
                        placeholder = stringResource(R.string.prompts_field_title_placeholder),
                        singleLine = true
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Text(
                            text = stringResource(R.string.subagent_editor_scope),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilterChip(
                            selected = scopeIsProject,
                            enabled = hasWorkspace,
                            onClick = { scopeIsProject = true },
                            label = { Text(stringResource(R.string.subagent_scope_project)) }
                        )
                        FilterChip(
                            selected = !scopeIsProject,
                            onClick = { scopeIsProject = false },
                            label = { Text(stringResource(R.string.subagent_scope_global)) }
                        )
                    }
                }
            }

            SettingsGroupHeader(text = stringResource(R.string.prompts_field_summary))
            SettingsGroup {
                AppTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = stringResource(R.string.prompts_field_summary_placeholder),
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = 12.dp)
                )
            }

            SettingsGroupHeader(text = stringResource(R.string.prompts_field_content))
            SettingsGroup {
                // 导入：从文件读入 md / txt 作为片段内容，省去手打长文本。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.lg, end = Spacing.lg, top = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf(
                                    "text/markdown", "text/plain", "text/x-markdown",
                                    "application/octet-stream"
                                )
                            )
                        }
                    ) {
                        Icon(
                            FeatherIcons.Upload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.prompts_import_file),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
                AppTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = stringResource(R.string.prompts_field_content_placeholder),
                    singleLine = false,
                    minLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = 12.dp)
                )
            }
        }
    }
}

/** 读取 content:// URI 的显示文件名；取不到时退回路径末段。 */
private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? {
    context.contentResolver
        .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) return name
            }
        }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
}
