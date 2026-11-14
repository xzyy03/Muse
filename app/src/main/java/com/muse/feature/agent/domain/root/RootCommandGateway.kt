package com.muse.feature.agent.domain.root

import com.muse.core.util.FileLogger
import java.util.UUID
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
    private val approvalManager: RootApprovalManager,
    private val consentStore: RootConsentStore
) {
    private companion object {
        const val TAG = "RootGateway"
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
                // 不再按路径逐条确认：用户同意一次「允许使用 root」后，之后全部直接执行。
                when (consentStore.current()) {
                    RootConsent.GRANTED -> {
                        audit(command, argv, "ALLOW", verdict.reason, "consent")
                        return runArgv(argv, timeoutMs)
                    }

                    RootConsent.DENIED -> {
                        audit(command, argv, "CONSENT_DENIED", "用户已关闭 root 权限", null)
                        return Result.failure(
                            SecurityException("root 权限已关闭；可在「软件权限 → Root 权限」中重新开启")
                        )
                    }

                    RootConsent.UNKNOWN -> {
                        val request = PendingRootApproval(
                            id = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            argv = argv,
                            targets = verdict.targets,
                            reason = verdict.reason,
                            isConsentRequest = true
                        )
                        val agreed = approvalManager.awaitApproval(request) != RootApprovalChoice.DENY
                        consentStore.set(if (agreed) RootConsent.GRANTED else RootConsent.DENIED)
                        if (!agreed) {
                            audit(command, argv, "USER_DENY", "用户拒绝了 root 权限", null)
                            return Result.failure(
                                SecurityException("已拒绝 root 权限；可在「软件权限 → Root 权限」中重新开启")
                            )
                        }
                        audit(command, argv, "ALLOW", verdict.reason, "consent")
                        return runArgv(argv, timeoutMs)
                    }
                }
            }
        }
    }

    private suspend fun runArgv(argv: List<String>, timeoutMs: Long): Result<RootExecResult> =
        runCatching { suRunner.run(argv, timeoutMs) }
            .mapCatching { result ->
                if (result.timedOut) throw IllegalStateException("root 命令超时")
                result
            }

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
