package com.muse.feature.settings.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.muse.core.datastore.preferencesCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.promptFragmentDataStore by preferencesDataStore(
    name = "prompt_fragment_prefs",
    corruptionHandler = preferencesCorruptionHandler
)

/**
 * 提示词片段的独立开关。
 *
 * 片段本身是磁盘上的 Markdown，不往里写开关状态（会被 AI 读到、也会让文件变脏）；
 * 这里单独记录「被关闭的片段编号」，渲染静态提示词时跳过这些编号。
 * 内置片段与自定义片段共用同一套编号空间，因此一个开关同时管住两者。
 */
@Singleton
class PromptFragmentToggleRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        val DISABLED_NUMBERS = stringSetPreferencesKey("disabled_fragment_numbers")
    }

    /** 被关闭的片段编号集合。 */
    val disabledNumbersFlow: Flow<Set<Int>> = context.promptFragmentDataStore.data.map { prefs ->
        prefs[DISABLED_NUMBERS].orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
    }

    /** 读取一次当前值（渲染时用）。 */
    suspend fun disabledNumbers(): Set<Int> = disabledNumbersFlow.first()

    suspend fun setEnabled(number: Int, enabled: Boolean) {
        context.promptFragmentDataStore.edit { prefs ->
            val current = prefs[DISABLED_NUMBERS].orEmpty().toMutableSet()
            if (enabled) current.remove(number.toString()) else current.add(number.toString())
            prefs[DISABLED_NUMBERS] = current
        }
    }

    /** 删除片段时一并清掉它的开关记录，避免编号复用后沿用旧状态。 */
    suspend fun forget(number: Int) {
        context.promptFragmentDataStore.edit { prefs ->
            val current = prefs[DISABLED_NUMBERS].orEmpty().toMutableSet()
            if (current.remove(number.toString())) prefs[DISABLED_NUMBERS] = current
        }
    }
}
