package com.muse.feature.agent.domain.root

import com.muse.feature.agent.data.local.dao.RootGrantDao
import com.muse.feature.agent.data.local.entity.RootGrantEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 授权查询与写入。
 *
 * `ALWAYS` 落库，跨进程有效；`SESSION` 只存在内存里，进程结束即失效，
 * 这样「本次会话始终允许」不会在下次启动后变成永久的默认放行。
 */
@Singleton
class RootGrantStore @Inject constructor(
    private val dao: RootGrantDao
) {
    private val sessionGrants = ConcurrentHashMap<String, MutableSet<String>>()

    private fun key(realPath: String, op: RootOp) = "$realPath\u0000${op.label}"

    /** 是否已授权：会话授权优先于数据库。 */
    suspend fun isGranted(sessionId: String, realPath: String, op: RootOp): Boolean {
        val sessionSet = sessionGrants[sessionId]
        if (sessionSet != null && synchronized(sessionSet) { key(realPath, op) in sessionSet }) {
            return true
        }
        return dao.count(realPath, op.label) > 0
    }

    suspend fun grant(sessionId: String, realPath: String, op: RootOp, scope: RootApprovalChoice) {
        when (scope) {
            RootApprovalChoice.ONCE -> Unit
            RootApprovalChoice.SESSION -> {
                val set = sessionGrants.getOrPut(sessionId) { mutableSetOf() }
                synchronized(set) { set.add(key(realPath, op)) }
            }
            RootApprovalChoice.ALWAYS -> dao.insert(
                RootGrantEntity(
                    id = UUID.randomUUID().toString(),
                    realPath = realPath,
                    operation = op.label,
                    scope = "always",
                    createdAt = System.currentTimeMillis(),
                    revocable = true
                )
            )
            RootApprovalChoice.DENY -> Unit
        }
    }

    fun clearSession(sessionId: String) {
        sessionGrants.remove(sessionId)
    }

    fun observeAll() = dao.observeAll()

    suspend fun revoke(id: String) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()
}
