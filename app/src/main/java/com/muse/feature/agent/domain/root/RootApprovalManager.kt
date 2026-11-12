package com.muse.feature.agent.domain.root

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * root 确认弹窗的挂起与唤醒。
 *
 * 工具线程在这里 park，UI 观察 [pending] 渲染面板；用户选择后 [resolve] 唤醒。
 * 同一时刻只允许一个待决请求，避免多个 root 调用同时把弹窗顶乱。
 */
@Singleton
class RootApprovalManager @Inject constructor() {

    private val mutex = Mutex()

    private val _pending = MutableStateFlow<PendingRootApproval?>(null)
    val pending: StateFlow<PendingRootApproval?> = _pending.asStateFlow()

    private var currentId: String? = null
    private var currentDecision: CompletableDeferred<RootApprovalChoice>? = null

    suspend fun awaitApproval(request: PendingRootApproval): RootApprovalChoice = mutex.withLock {
        val decision = CompletableDeferred<RootApprovalChoice>()
        currentId = request.id
        currentDecision = decision
        _pending.value = request
        try {
            decision.await()
        } finally {
            if (currentId == request.id) {
                currentId = null
                currentDecision = null
                _pending.value = null
            }
        }
    }

    @Synchronized
    fun resolve(id: String, choice: RootApprovalChoice) {
        if (currentId != id) return
        val decision = currentDecision ?: return
        if (!decision.isCompleted) decision.complete(choice)
    }

    /** 会话被取消/停止时调用，避免工具线程永远挂起。 */
    @Synchronized
    fun cancelPending() {
        val decision = currentDecision ?: return
        if (!decision.isCompleted) decision.complete(RootApprovalChoice.DENY)
    }
}
