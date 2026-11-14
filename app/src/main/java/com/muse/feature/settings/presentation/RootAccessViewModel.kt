package com.muse.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muse.feature.agent.domain.root.RootConsent
import com.muse.feature.agent.domain.root.RootConsentStore
import com.muse.feature.agent.domain.root.SuRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RootAccessState(
    val checking: Boolean = true,
    val available: Boolean = false,
    val consent: RootConsent = RootConsent.UNKNOWN
)

/** 软件权限页的 Root 入口：检测 su 可用性，管理 root 使用授权开关。 */
@HiltViewModel
class RootAccessViewModel @Inject constructor(
    private val suRunner: SuRunner,
    private val consentStore: RootConsentStore
) : ViewModel() {

    private val _state = MutableStateFlow(RootAccessState())
    val state: StateFlow<RootAccessState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            val available = runCatching { suRunner.isAvailable() }.getOrDefault(false)
            _state.update {
                it.copy(
                    checking = false,
                    available = available,
                    consent = consentStore.current()
                )
            }
        }
    }

    /** 设置页手动开启 root 权限。 */
    fun grantConsent() {
        consentStore.set(RootConsent.GRANTED)
        _state.update { it.copy(consent = RootConsent.GRANTED) }
    }

    /** 关闭 root 权限：之后所有 root 调用都会被拒绝，直到重新开启。 */
    fun revokeConsent() {
        consentStore.set(RootConsent.DENIED)
        _state.update { it.copy(consent = RootConsent.DENIED) }
    }
}
