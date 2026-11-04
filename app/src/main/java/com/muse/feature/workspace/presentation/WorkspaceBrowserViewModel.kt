package com.muse.feature.workspace.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muse.core.util.FileLogger
import com.muse.feature.workspace.data.repository.SessionProjectBindingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** 文件浏览器里的一行。 */
data class WorkspaceEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

/** 文件浏览页面的状态：当前目录、条目、上级目录与操作反馈。 */
data class WorkspaceBrowserState(
    val rootPath: String = "",
    val currentPath: String = "",
    val entries: List<WorkspaceEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** 尚未选定项目：此时列出工作区下的候选项目目录供用户挑一个。 */
    val selectingProject: Boolean = false,
    /** 只读浏览：查看其它项目资料时置位，禁用新建/重命名/删除，且不改变会话绑定。 */
    val readOnly: Boolean = false
) {
    /** 是否可以返回上级（到根为止）。 */
    val canGoUp: Boolean get() = rootPath.isNotBlank() && currentPath != rootPath
}

/**
 * 工作区文件浏览器。
 *
 * 对话绑定的是「工作区」，而工作区目录下通常还套着多个项目文件夹。因此这里区分两种状态：
 * - 已绑定项目：直接把该项目作为浏览根，长按进来就是这一个项目的文件；
 * - 未绑定：列出工作区下的候选目录，用户点一个即完成绑定并记住，之后长按直达。
 *
 * 全程使用宿主绝对路径（`java.io.File`），不经容器路径映射。
 */
@HiltViewModel
class WorkspaceBrowserViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val bindingRepository: SessionProjectBindingRepository
) : ViewModel() {

    private companion object {
        const val TAG = "WorkspaceBrowser"
        /** 超过该数量才认为工作区下是「多个项目」；少量目录直接当作项目内容浏览。 */
        const val PROJECT_LIST_THRESHOLD = 1
    }

    private val _state = MutableStateFlow(WorkspaceBrowserState())
    val state: StateFlow<WorkspaceBrowserState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var sessionId: String = ""
    private var workspaceRoot: String = ""

    /**
     * 打开文件浏览。
     *
     * @param sessionId 当前会话 id，用于读写它绑定的项目
     * @param workspacePath 会话所属工作区的宿主绝对路径
     */
    fun open(sessionId: String, workspacePath: String?) {
        this.sessionId = sessionId
        val root = workspacePath.orEmpty().trimEnd('/')
        FileLogger.i(TAG, "打开工作区: $root (session=$sessionId)")
        if (root.isBlank()) {
            _state.value = WorkspaceBrowserState(error = "当前对话尚未创建工作区")
            return
        }
        workspaceRoot = root
        viewModelScope.launch {
            val bound = bindingRepository.boundPath(sessionId).first()
                ?.takeIf { it.isNotBlank() && File(it).isDirectory }
            if (bound != null) {
                _state.value = WorkspaceBrowserState(rootPath = bound, currentPath = bound, loading = true)
                loadEntries(bound)
            } else {
                openProjectPicker(root)
            }
        }
    }

    /** 未绑定项目时：列出工作区下的候选目录，让用户选一个当作本对话的项目。 */
    private suspend fun openProjectPicker(root: String) {
        val dirs = withContext(Dispatchers.IO) {
            File(root).listFiles().orEmpty().filter { it.isDirectory }
        }
        when {
            // 工作区里只有一个目录：直接认为它就是项目，自动绑定。
            dirs.size == PROJECT_LIST_THRESHOLD && dirs.isNotEmpty() -> {
                bindingRepository.bind(sessionId, dirs.first().absolutePath)
                _state.value = WorkspaceBrowserState(
                    rootPath = dirs.first().absolutePath,
                    currentPath = dirs.first().absolutePath,
                    loading = true
                )
                loadEntries(dirs.first().absolutePath)
            }
            // 多个目录：让用户挑一个。
            dirs.isNotEmpty() -> {
                _state.value = WorkspaceBrowserState(
                    rootPath = root,
                    currentPath = root,
                    entries = dirs.map { it.toEntry() }.sortedBy { it.name.lowercase() },
                    loading = false,
                    selectingProject = true
                )
            }
            // 没有任何子目录：工作区本身就是项目，直接浏览。
            else -> {
                _state.value = WorkspaceBrowserState(rootPath = root, currentPath = root, loading = true)
                loadEntries(root)
            }
        }
    }
    /** 打开某个工作区下的子项目做只读浏览（不改变会话绑定）。 */
    fun browseOther(projectPath: String) {
        viewModelScope.launch {
            _state.value = WorkspaceBrowserState(
                rootPath = projectPath.trimEnd('/'),
                currentPath = projectPath.trimEnd('/'),
                loading = true,
                readOnly = true
            )
            loadEntries(projectPath.trimEnd('/'))
        }
    }

    /** 列出本工作区下的其它项目，供“浏览其它项目”使用。 */
    suspend fun listSiblingProjects(): List<WorkspaceEntry> = withContext(Dispatchers.IO) {
        File(workspaceRoot).listFiles().orEmpty()
            .filter { it.isDirectory }
            .map { it.toEntry() }
            .sortedBy { it.name.lowercase() }
    }

    /** 选中某个目录作为本对话的项目，并记住绑定。 */
    fun selectProject(entry: WorkspaceEntry) {
        viewModelScope.launch {
            bindingRepository.bind(sessionId, entry.path)
            _state.value = WorkspaceBrowserState(
                rootPath = entry.path,
                currentPath = entry.path,
                loading = true
            )
            loadEntries(entry.path)
        }
    }

    /** 解除项目绑定，回到项目选择列表。 */
    fun changeProject() {
        viewModelScope.launch {
            bindingRepository.unbind(sessionId)
            if (workspaceRoot.isBlank()) return@launch
            openProjectPicker(workspaceRoot)
        }
    }

    fun goUp() {
        val s = _state.value
        if (!s.canGoUp) return
        val parent = File(s.currentPath).parent.orEmpty().ifBlank { s.rootPath }
        enter(parent)
    }

    fun enter(path: String) {
        val s = _state.value
        if (!isWithinRoot(s.rootPath, path)) return
        viewModelScope.launch {
            _state.value = s.copy(currentPath = path, loading = true, error = null, selectingProject = false)
            loadEntries(path)
        }
    }

    fun refresh() {
        val s = _state.value
        if (s.currentPath.isBlank()) return
        viewModelScope.launch {
            _state.value = s.copy(loading = true)
            loadEntries(s.currentPath)
        }
    }

    private suspend fun loadEntries(path: String) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(path)
                if (!dir.isDirectory) throw IllegalStateException("目录不存在或不可读")
                dir.listFiles().orEmpty()
                    .map { it.toEntry() }
                    .sortedWith(
                        compareByDescending<WorkspaceEntry> { it.isDirectory }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    )
            }
        }
        _state.value = result.fold(
            onSuccess = { _state.value.copy(entries = it, loading = false, error = null) },
            onFailure = { e ->
                FileLogger.w(TAG, "列目录失败: $path", e)
                _state.value.copy(entries = emptyList(), loading = false, error = e.message ?: "无法读取目录")
            }
        )
    }

    fun createFolder(name: String) = createEntry(name, directory = true)

    fun createFile(name: String) = createEntry(name, directory = false)

    fun rename(entry: WorkspaceEntry, newName: String) {
        val s = _state.value
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.contains('/')) {
            _message.value = "名称不合法"
            return
        }
        val target = joinPath(s.currentPath, trimmed)
        if (!isWithinRoot(s.rootPath, target)) {
            _message.value = "名称不合法"
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { File(entry.path).renameTo(File(target)) }.getOrDefault(false)
            }
            _message.value = if (ok) "已重命名" else "重命名失败"
            if (ok) loadEntries(s.currentPath)
        }
    }

    fun delete(entry: WorkspaceEntry) {
        val s = _state.value
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val f = File(entry.path)
                    if (entry.isDirectory) f.deleteRecursively() else f.delete()
                }.getOrDefault(false)
            }
            _message.value = if (ok) "已删除" else "删除失败"
            if (ok) loadEntries(s.currentPath)
        }
    }

    private fun isWithinRoot(root: String, path: String): Boolean {
        if (root.isBlank()) return false
        val rootPath = File(root).absolutePath.trimEnd('/')
        val target = File(path).absolutePath.trimEnd('/')
        return target == rootPath || target.startsWith("$rootPath/")
    }

    private fun createEntry(name: String, directory: Boolean) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.contains('/')) {
            _message.value = "名称不合法"
            return
        }
        val s = _state.value
        val target = joinPath(s.currentPath, trimmed)
        if (!isWithinRoot(s.rootPath, target)) {
            _message.value = "名称不合法"
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    if (directory) File(target).mkdirs()
                    else {
                        File(target).parentFile?.mkdirs()
                        File(target).createNewFile()
                    }
                }.getOrDefault(false)
            }
            _message.value = when {
                !ok -> if (directory) "创建文件夹失败" else "创建文件失败"
                directory -> "已创建文件夹"
                else -> "已创建文件"
            }
            if (ok) loadEntries(s.currentPath)
        }
    }

    fun hostDir(path: String): File = File(path)

    fun consumeMessage() {
        _message.value = null
    }

    private fun File.toEntry() = WorkspaceEntry(
        name = name,
        path = absolutePath,
        isDirectory = isDirectory,
        size = if (isDirectory) 0L else length(),
        lastModified = lastModified()
    )

    private fun joinPath(dir: String, name: String): String =
        if (dir.endsWith("/")) dir + name else "$dir/$name"
}
