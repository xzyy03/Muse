package com.muse.feature.pet.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.muse.core.datastore.preferencesCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.petDataStore by preferencesDataStore(
    name = "pet_overlay_prefs",
    corruptionHandler = preferencesCorruptionHandler
)

/**
 * 鲸娘悬浮窗偏好：开关、显示尺寸、上次拖动到的位置。
 *
 * 尺寸以 dp 存储，最小 1dp（用户要求可缩到极小）；位置存归一化坐标（0~1），
 * 换屏幕尺寸后仍落在相近的相对位置。
 */
@Singleton
class PetOverlaySettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        /** 默认显示尺寸（dp）。 */
        const val DEFAULT_SIZE_DP = 35f
        /** 允许的最小尺寸（dp）：用户要求最小可到 1dp。 */
        const val MIN_SIZE_DP = 1f
        /** 允许的最大尺寸（dp）。 */
        const val MAX_SIZE_DP = 320f

        private val ENABLED = booleanPreferencesKey("pet_enabled")
        private val SIZE_DP = floatPreferencesKey("pet_size_dp")
        private val POS_X = floatPreferencesKey("pet_pos_x")
        private val POS_Y = floatPreferencesKey("pet_pos_y")
        private val AUTO_BIND = booleanPreferencesKey("pet_auto_bind_to_card")
    }

    /** 是否开启悬浮窗。 */
    val enabledFlow: Flow<Boolean> = context.petDataStore.data.map { it[ENABLED] ?: true }

    /** 显示尺寸（dp），已按上下限夹取。 */
    val sizeDpFlow: Flow<Float> = context.petDataStore.data.map { prefs ->
        (prefs[SIZE_DP] ?: DEFAULT_SIZE_DP).coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
    }

    /** 归一化位置；null 表示尚未手动拖动过，用默认位置（输入框上方）。 */
    val positionFlow: Flow<Pair<Float, Float>?> = context.petDataStore.data.map { prefs ->
        val x = prefs[POS_X]
        val y = prefs[POS_Y]
        if (x == null || y == null) null else x to y
    }

    /** 是否在出现待办卡片时自动上移到卡片之上。 */
    val autoBindToCardFlow: Flow<Boolean> = context.petDataStore.data.map {
        it[AUTO_BIND] ?: true
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.petDataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun setSizeDp(sizeDp: Float) {
        context.petDataStore.edit { it[SIZE_DP] = sizeDp.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP) }
    }

    suspend fun setPosition(normalizedX: Float, normalizedY: Float) {
        context.petDataStore.edit {
            it[POS_X] = normalizedX.coerceIn(0f, 1f)
            it[POS_Y] = normalizedY.coerceIn(0f, 1f)
        }
    }

    suspend fun setAutoBindToCard(enabled: Boolean) {
        context.petDataStore.edit { it[AUTO_BIND] = enabled }
    }
}
