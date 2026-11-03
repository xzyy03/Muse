package com.muse.feature.settings.data.repository

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

private val Context.chatBubbleDataStore by preferencesDataStore(
    name = "chat_bubble_prefs",
    corruptionHandler = preferencesCorruptionHandler
)

/** 聊天气泡样式：内置若干套配色/形状，用户在「通用设置 → 聊天气泡」切换。 */
enum class ChatBubbleStyle(val id: String, val displayName: String) {
    /** 白底描边气泡（原版样式）。 */
    CLASSIC("classic", "经典白描边"),
    /** 白底 + 投影，字形更圆润。 */
    SHADOW("shadow", "柔和投影"),
    /** 纯白无描边，极简。 */
    MINIMAL("minimal", "极简纯白"),
    /** 黑底白字。 */
    DARK("dark", "暗夜黑"),
    /** 墨线方格 + 右侧箭头，漫画风。 */
    SQUARE("square", "漫画方格"),
    /** 微信绿气泡。 */
    GREEN("green", "清新绿"),
    /** 心动时刻。 */
    VALENTINE("valentine", "心动时刻"),
    /** 原版 AiCode 气泡：主题容器色底、无描边、小圆角。 */
    ORIGINAL("original", "原版 AiCode");

    companion object {
        fun fromId(id: String?): ChatBubbleStyle =
            entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/**
 * 聊天气泡样式的持久化：仅存一个样式 id，其余渲染细节由 [ChatBubbleStyle] 决定。
 */
@Singleton
class ChatBubbleSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        val STYLE_KEY = stringPreferencesKey("chat_bubble_style")
    }

    val styleFlow: Flow<ChatBubbleStyle> = context.chatBubbleDataStore.data.map { prefs ->
        ChatBubbleStyle.fromId(prefs[STYLE_KEY])
    }

    suspend fun setStyle(style: ChatBubbleStyle) {
        context.chatBubbleDataStore.edit { it[STYLE_KEY] = style.id }
    }
}
