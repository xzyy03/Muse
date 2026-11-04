package com.muse.feature.workspace.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.muse.core.datastore.preferencesCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionProjectDataStore by preferencesDataStore(
    name = "session_project_prefs",
    corruptionHandler = preferencesCorruptionHandler
)

/**
 * 会话与「工作区内的子项目目录」的绑定关系。
 *
 * 工作区（`filesDir/projects/<name>`）本身可能容纳多个项目目录，
 * 会话除了绑定工作区之外，还可以记一个更具体的子目录，长按工作区图标时直接打开它。
 * 未绑定时回退到会话的工作区根。
 */
@Singleton
class SessionProjectBindingRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        const val KEY_PREFIX = "session_project_"
    }

    /** 读取某会话绑定的子项目路径（宿主绝对路径）；未绑定返回 null。 */
    fun boundPath(sessionId: String): Flow<String?> {
        if (sessionId.isBlank()) return kotlinx.coroutines.flow.flowOf(null)
        return context.sessionProjectDataStore.data.map { prefs ->
            prefs[stringPreferencesKey(KEY_PREFIX + sessionId)]?.takeIf { it.isNotBlank() }
        }
    }

    suspend fun bind(sessionId: String, projectPath: String) {
        if (sessionId.isBlank()) return
        context.sessionProjectDataStore.edit {
            it[stringPreferencesKey(KEY_PREFIX + sessionId)] = projectPath
        }
    }

    suspend fun unbind(sessionId: String) {
        if (sessionId.isBlank()) return
        context.sessionProjectDataStore.edit {
            it.remove(stringPreferencesKey(KEY_PREFIX + sessionId))
        }
    }
}
