package com.muse.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muse.feature.agent.domain.plugin.Plugin
import com.muse.feature.agent.domain.plugin.MarketPlugin
import com.muse.feature.agent.domain.plugin.PluginMarketRepository
import com.muse.feature.agent.domain.plugin.PluginRepository
import com.muse.feature.agent.domain.plugin.PluginToolRegistrar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 插件页状态：磁盘上扫到的插件、禁用名单与插件根目录。 */
data class PluginsUiState(
    val plugins: List<Plugin> = emptyList(),
    val disabledIds: Set<String> = emptySet(),
    val rootPath: String = "",
    val loading: Boolean = true,
    /** 市场索引（未拉取时为空）。 */
    val market: List<MarketPlugin> = emptyList(),
    val marketLoading: Boolean = false,
    val marketLoaded: Boolean = false,
    val marketError: String? = null,
    /** 市场搜索关键词。 */
    val marketQuery: String = "",
    /** 正在安装的插件 id。 */
    val installingId: String? = null
)

/**
 * 插件页状态：扫描 `~/.muse/plugins/` 并读写启停。
 *
 * 每次变更后都调 [PluginToolRegistrar.refresh]，让工具箱立刻反映插件开关，
 * 不必重开 App 或新开会话。
 */
@HiltViewModel
class PluginsViewModel @Inject constructor(
    private val repository: PluginRepository,
    private val registrar: PluginToolRegistrar,
    private val marketRepository: PluginMarketRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PluginsUiState())
    val state: StateFlow<PluginsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                PluginsUiState(
                    plugins = repository.list(),
                    disabledIds = repository.disabledIds(),
                    rootPath = repository.pluginsRootPath(),
                    loading = false
                )
            }
            _state.value = loaded
            withContext(Dispatchers.IO) { registrar.refresh() }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.setEnabled(id, enabled)
                registrar.refresh()
            }
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.delete(id)
                registrar.refresh()
            }
            refresh()
        }
    }

    /** 拉取市场索引；已拉过则不重复请求，除非 force。 */
    fun loadMarket(force: Boolean = false) {
        if (!force && _state.value.marketLoaded && _state.value.marketError == null) return
        _state.update { it.copy(marketLoading = true, marketError = null) }
        viewModelScope.launch {
            val result = marketRepository.loadIndex()
            _state.update {
                result.fold(
                    onSuccess = { list ->
                        it.copy(market = list, marketLoading = false, marketLoaded = true, marketError = null)
                    },
                    onFailure = { err ->
                        it.copy(marketLoading = false, marketError = err.message ?: "索引拉取失败")
                    }
                )
            }
        }
    }

    fun setMarketQuery(query: String) {
        _state.update { it.copy(marketQuery = query) }
    }

    /** 当前搜索结果。 */
    fun searchResults(): List<MarketPlugin> {
        val s = _state.value
        return marketRepository.search(s.market, s.marketQuery)
    }

    /** 安装市场插件，装完自动刷新已安装列表与工具箱。 */
    fun installMarket(plugin: MarketPlugin) {
        if (_state.value.installingId != null) return
        _state.update { it.copy(installingId = plugin.id) }
        viewModelScope.launch {
            val result = marketRepository.install(plugin)
            _state.update { it.copy(installingId = null) }
            if (result.isSuccess) {
                withContext(Dispatchers.IO) { registrar.refresh() }
                refresh()
            } else {
                _state.update { state ->
                    state.copy(marketError = "安装失败：" + (result.exceptionOrNull()?.message ?: "未知错误"))
                }
            }
        }
    }
}
