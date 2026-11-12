package com.muse.feature.agent.domain.root

/**
 * root 通道的操作类型。授权按「realpath + 操作」为粒度记忆，
 * 因为「允许写」和「允许删」的风险不同，不能共用一条授权。
 */
enum class RootOp(val label: String) {
    READ("read"),
    WRITE("write"),
    DELETE("delete"),
    MOVE("move"),
    RENAME("rename"),
    MOUNT("mount"),
    PERMISSION("chmod"),
    OTHER("other")
}

/** 一次 root 调用解析出的目标：规范化后的绝对路径 + 操作类型。 */
data class RootTarget(val realPath: String, val op: RootOp)

/** 策略判定结果。DENY 优先于一切授权记录，无法被「始终允许」覆盖。 */
sealed interface RootVerdict {
    data class Allow(val reason: String) : RootVerdict
    data class Ask(val targets: List<RootTarget>, val reason: String) : RootVerdict
    data class Deny(val reason: String) : RootVerdict
}

/** 用户在确认弹窗里的选择。 */
enum class RootApprovalChoice {
    /** 仅本次。 */
    ONCE,

    /** 本次会话内对同一路径同一操作不再询问。 */
    SESSION,

    /** 永久记录该路径该操作。 */
    ALWAYS,

    /** 拒绝本次执行。 */
    DENY
}

/** 待用户确认的 root 请求（UI 只读展示，不做判定）。 */
data class PendingRootApproval(
    val id: String,
    val sessionId: String,
    val argv: List<String>,
    val targets: List<RootTarget>,
    val reason: String,
    /** 选「始终允许」时实际写入授权表的范围：目录（覆盖写与删），或单个路径。 */
    val grantScope: String? = null
)

/** su 执行结果。 */
data class RootExecResult(
    val output: String,
    val exitCode: Int,
    val timedOut: Boolean = false
)
