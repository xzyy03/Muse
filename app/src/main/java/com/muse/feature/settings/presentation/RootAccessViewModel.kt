package com.muse.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muse.feature.agent.data.local.entity.RootGrantEntity
import com.muse.feature.agent.domain.root.RootCommandGateway
import com.muse.feature.agent.domain.root.RootGrantStore
import com.muse.feature.agent.domain.root.SuRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RootAccessState(
    val checking: Boolean = true,
    val available: Boolean = false,
    val grants: List<RootGrantEntity> = emptyList()
)

/** 软件权限页的 Root 入口：检测 su 可用性并管理「始终允许」记录。 */
@HiltViewModel
class RootAccessViewModel @Inject constructor(
    private val suRunner: SuRunner,
    private val grantStore: RootGrantStore,
    private val gateway: RootCommandGateway
) : ViewModel() {

    private val _state = MutableStateFlow(RootAccessState())
    val state: StateFlow<RootAccessState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            grantStore.observeAll().collect { grants ->
                _state.update { it.copy(grants = grants) }
            }
        }
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            val available = runCatching { suRunner.isAvailable() }.getOrDefault(false)
            _state.update { it.copy(checking = false, available = available) }
        }
    }

    fun revoke(id: String) {
        viewModelScope.launch { grantStore.revoke(id) }
    }

    fun clearAll() {
        viewModelScope.launch { grantStore.clearAll() }
    }

    /** 解除因连续拒绝触发的暂停。 */
    fun resetFuse() = gateway.resetAllFuses()
}
