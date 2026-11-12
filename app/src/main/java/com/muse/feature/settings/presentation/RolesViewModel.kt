package com.muse.feature.settings.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muse.feature.agent.domain.role.Role
import com.muse.feature.agent.domain.role.RoleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.muse.core.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class RolesUiState(
    val roles: List<Role> = emptyList(),
    val currentId: String = ""
) {
    val current: Role? get() = roles.firstOrNull { it.id == currentId } ?: roles.firstOrNull()
}

/** 角色管理：增删、选中、头像与身份提示词。身份直接参与系统提示词组装。 */
@HiltViewModel
class RolesViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: RoleRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RolesUiState())
    val state: StateFlow<RolesUiState> = _state.asStateFlow()

    /** 待裁剪的原图字节；非空时 UI 打开裁剪界面。 */
    private val _pendingCrop = MutableStateFlow<PendingAvatar?>(null)
    val pendingCrop: StateFlow<PendingAvatar?> = _pendingCrop.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val roles = repository.list()
        _state.value = RolesUiState(roles = roles, currentId = repository.current().id)
    }

    fun select(id: String) {
        repository.setCurrent(id)
        refresh()
    }

    fun save(role: Role) {
        repository.upsert(role)
        refresh()
    }

    fun create(name: String): Role {
        val role = repository.newRole(name)
        repository.upsert(role)
        refresh()
        return role
    }

    fun delete(id: String): Boolean {
        val ok = repository.delete(id)
        if (ok) refresh()
        return ok
    }

    /** 选中图片后先读出字节，交给裁剪界面；不在此时落盘。 */
    fun pickAvatar(id: String, uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes != null && bytes.isNotEmpty()) {
                _pendingCrop.value = PendingAvatar(roleId = id, source = bytes)
            }
        }
    }

    fun cancelCrop() {
        _pendingCrop.value = null
    }

    /** 裁剪完成：写盘并同步更新角色记录。 */
    fun commitAvatar(croppedPng: ByteArray) {
        val pending = _pendingCrop.value ?: return
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { repository.updateAvatar(pending.roleId, croppedPng) }
            _pendingCrop.value = null
            refresh()
            if (path != null) FileLogger.d("RolesViewModel", "头像已更新: $path")
        }
    }

    fun removeAvatar(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.removeAvatar(id) }
            refresh()
        }
    }
}

data class PendingAvatar(val roleId: String, val source: ByteArray)
