package com.muse.feature.agent.domain.root

import com.muse.core.util.FileLogger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * root 执行的唯一入口。
 *
 * 顺序固定：分词 → 解析路径 → 策略判定（DENY 优先）→ 授权表 → 必要时弹窗 → su 执行 → 审计。
 * 任何一步失败都不会退回普通权限执行。
 */
@Singleton
class RootCommandGateway @Inject constructor(
    private val suRunner: SuRunner,
    private val pathResolver: RootPathResolver,
    private val policyEngine: RootPolicyEngine,
    private val grantStore: RootGrantStore,
    private val approvalManager: RootApprovalManager
) {
    private companion object {
        const val TAG = "RootGateway"
        const val FUSE_THRESHOLD = 3
        val DENY_LOG = ConcurrentHashMap<String, Int>()
    }

    suspend fun execute(
        command: String,
        sessionId: String,
        timeoutMs: Long
    ): Result<RootExecResult> {
        val tokenized = RootTokenizer.tokenize(command)
        if (tokenized is RootTokenizer.Result.Rejected) {
            audit(command, emptyList(), "REJECTED", tokenized.reason, null)
            return Result.failure(IllegalArgumentException(tokenized.reason))
        }
        val argv = (tokenized as RootTokenizer.Result.Ok).argv

        if (!suRunner.isAvailable()) {
            audit(command, argv, "NO_ROOT", "设备未提供可用的 su", null)
            return Result.failure(IllegalStateException("未获得 root：设备上找不到可用的 su，或尚未授权"))
        }

        val verdict = policyEngine.evaluate(argv) { pathResolver.resolve(it) }
        when (verdict) {
            is RootVerdict.Deny -> {
                audit(command, argv, "DENY", verdict.reason, null)
                return Result.failure(SecurityException("已拦截：${verdict.reason}"))
            }
            is RootVerdict.Allow -> {
                audit(command, argv, "ALLOW", verdict.reason, "builtin")
                return runArgv(argv, timeoutMs)
            }
            is RootVerdict.Ask -> {
                if (fusedOut(sessionId)) {
                    audit(command, argv, "FUSE", "连续拒绝达到上限，会话已暂停", null)
                    return Result.failure(IllegalStateException("连续多次拒绝 root 操作，会话已暂停；请在「软件权限 → Root 权限」中重新确认后再试"))
                }
                // 没有路径操作数的命令（如 settings put 之类被解析成 OTHER）也要能被「始终允许」记住，
                // 否则 targets 为空 → 每次都弹窗，用户点了授权也像没生效。
                val grantTargets = verdict.targets.ifEmpty {
                    listOf(RootTarget("cmd:${argv.first()}", RootOp.OTHER))
                }
                val alreadyGranted = grantTargets.all { target ->
                    when (target.op) {
                        RootOp.WRITE, RootOp.DELETE ->
                            grantStore.isGranted(sessionId, grantScopeOf(target.realPath), target.op)
                        else -> grantStore.isGranted(sessionId, target.realPath, target.op)
                    }
                }
                FileLogger.d(
                    TAG,
                    "ask sid=$sessionId targets=${grantTargets.map { "${it.op.label}@${it.realPath}" }} " +
                        "granted=$alreadyGranted"
                )
                if (alreadyGranted) {
                    audit(command, argv, "ALLOW", "命中已有授权", "grant")
                    return runArgv(argv, timeoutMs)
                }

                val request = PendingRootApproval(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    argv = argv,
                    targets = verdict.targets,
                    reason = verdict.reason,
                    grantScope = grantTargets
                        .firstOrNull { it.op == RootOp.WRITE || it.op == RootOp.DELETE }
                        ?.let { grantScopeOf(it.realPath) }
                )
                val choice = approvalManager.awaitApproval(request)
                if (choice == RootApprovalChoice.DENY) {
                    bumpDeny(sessionId)
                    audit(command, argv, "USER_DENY", verdict.reason, null)
                    return Result.failure(SecurityException("用户拒绝了这次 root 操作"))
                }
                grantTargets.forEach { target ->
                    when (target.op) {
                        RootOp.WRITE, RootOp.DELETE -> {
                            // 用户选择的是「该目录的写入与删除」：一次确认覆盖目录下的新建、
                            // 覆盖文件与删除，避免每写一个文件就弹一次。
                            val scope = grantScopeOf(target.realPath)
                            grantStore.grant(sessionId, scope, RootOp.WRITE, choice)
                            grantStore.grant(sessionId, scope, RootOp.DELETE, choice)
                        }
                        else -> grantStore.grant(sessionId, target.realPath, target.op, choice)
                    }
                }
                if (choice != RootApprovalChoice.ONCE) resetDeny(sessionId)
                audit(command, argv, "ALLOW", verdict.reason, choice.name.lowercase())
                return runArgv(argv, timeoutMs)
            }
        }
    }

    private suspend fun runArgv(argv: List<String>, timeoutMs: Long): Result<RootExecResult> =
        runCatching { suRunner.run(argv, timeoutMs) }
            .mapCatching { result ->
                if (result.timedOut) throw IllegalStateException("root 命令超时")
                result
            }

    /**
     * 目录级授权范围：取目标所在目录。
     *
     * 共享根自身（`/storage/emulated/0` 及其上层）不能作为授权范围，否则一次确认等于放行整个存储；
     * 这种情况下退回文件级授权。
     */
    private fun grantScopeOf(realPath: String): String {
        val parent = realPath.substringBeforeLast('/', "")
        if (parent.isEmpty()) return realPath
        if (parent in setOf("/", "/storage", "/storage/emulated", "/storage/emulated/0")) return realPath
        return parent
    }

    private fun fusedOut(sessionId: String): Boolean =
        (DENY_LOG[sessionId] ?: 0) >= FUSE_THRESHOLD

    private fun bumpDeny(sessionId: String) {
        DENY_LOG[sessionId] = (DENY_LOG[sessionId] ?: 0) + 1
    }

    private fun resetDeny(sessionId: String) {
        DENY_LOG.remove(sessionId)
    }

    /** 用户重新确认后解除熔断（软件权限页调用）。 */
    fun resetFuse(sessionId: String) = resetDeny(sessionId)

    /** 权限页的「解除暂停」对全部会话生效。 */
    fun resetAllFuses() = DENY_LOG.clear()

    private fun audit(
        rawCommand: String,
        argv: List<String>,
        verdict: String,
        reason: String,
        grantSource: String?
    ) {
        FileLogger.i(
            TAG,
            "audit verdict=$verdict reason=$reason grant=${grantSource ?: "-"} " +
                "argv=[${argv.joinToString(" ")}] raw=${rawCommand.take(300)}"
        )
    }
}
