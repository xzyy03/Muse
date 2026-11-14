package com.muse.feature.agent.domain.root

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 用户对「使用 root」这件事本身的总体态度。 */
enum class RootConsent {
    /** 还没问过：第一次真正需要 root 时弹一次风险告知。 */
    UNKNOWN,

    /** 已同意：之后所有 root 调用直接执行，不再逐个路径确认。 */
    GRANTED,

    /** 已拒绝：root 通道关闭，直到用户在设置里手动开启。 */
    DENIED
}

/**
 * root 使用授权。
 *
 * 与 [RootGrantStore] 不同：那边记的是「哪个路径的哪种操作被允许」，
 * 这边只有一条全局开关——用户同意一次就代表接受 root 的风险，
 * 不再按路径反复询问。
 */
@Singleton
class RootConsentStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("root_access", Context.MODE_PRIVATE)

    fun current(): RootConsent {
        val raw = prefs.getString(KEY_CONSENT, null) ?: return RootConsent.UNKNOWN
        return runCatching { RootConsent.valueOf(raw) }.getOrDefault(RootConsent.UNKNOWN)
    }

    fun set(consent: RootConsent) {
        prefs.edit().putString(KEY_CONSENT, consent.name).apply()
    }
}

private const val KEY_CONSENT = "consent"
