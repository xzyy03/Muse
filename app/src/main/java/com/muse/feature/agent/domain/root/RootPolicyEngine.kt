package com.muse.feature.agent.domain.root

import javax.inject.Inject
import javax.inject.Singleton

/**
 * root 通道策略判定。
 *
 * 只有三种结论，且 DENY 优先：命中保护区的写类操作直接阻断，
 * 不查询授权表、也不能被任何「始终允许」记录覆盖。
 */
@Singleton
class RootPolicyEngine @Inject constructor() {

    private companion object {
        /** 只读命令：允许在任何位置执行，仅记审计。 */
        val READ_ONLY_COMMANDS = setOf(
            "ls", "cat", "stat", "getprop", "ps", "id", "whoami", "uname", "df", "du",
            "readlink", "realpath", "find", "grep", "head", "tail", "wc", "md5sum",
            "sha256sum", "lsattr", "logcat", "dumpsys", "uptime", "date", "echo",
            "printf", "which", "type", "printenv", "mount"
        )

        /** 写类命令及其操作类型。 */
        val WRITE_COMMANDS = mapOf(
            "cp" to RootOp.WRITE,
            "mkdir" to RootOp.WRITE,
            "touch" to RootOp.WRITE,
            "tee" to RootOp.WRITE,
            "dd" to RootOp.WRITE,
            "install" to RootOp.WRITE,
            "ln" to RootOp.WRITE
        )
        val DELETE_COMMANDS = setOf("rm", "rmdir")
        val MOVE_COMMANDS = setOf("mv")
        val PERMISSION_COMMANDS = setOf("chmod", "chown", "chgrp", "setenforce")
        val MOUNT_COMMANDS = setOf("umount")

        /** 写禁区：这些前缀之下任何写/删/改权限都直接拒绝。 */
        val SYSTEM_PREFIXES = listOf(
            "/system", "/vendor", "/product", "/system_ext", "/boot",
            "/init", "/init.rc", "/data/adb", "/dev", "/proc", "/sys", "/oem", "/odm"
        )

        /** 共享存储根：realpath 归一后的写类操作要弹窗。 */
        const val SHARED_ROOT = "/storage/emulated/0"

        /** root 通道的工作区：这里的普通写入直接放行。 */
        val WORKSPACE_PREFIXES = listOf(
            "/data/local/tmp",
            "/storage/emulated/0/Android/data/com.muse"
        )
    }

    /**
     * @param argv 已 token 化的命令
     * @param resolve 把原始路径交给 root 侧 realpath，失败返回 null
     */
    suspend fun evaluate(argv: List<String>, resolve: suspend (String) -> String?): RootVerdict {
        if (argv.isEmpty()) return RootVerdict.Deny("命令为空")
        val program = argv.first().substringAfterLast('/')

        if (program in MOUNT_COMMANDS) return RootVerdict.Deny("root 通道不允许卸载或重挂载文件系统")
        if (program == "mount") {
            val flags = argv.drop(1)
            val writable = flags.any { it == "-w" || it == "--rw" } ||
                flags.any { it.contains("rw") && it.contains("remount") } ||
                flags.contains("remount")
            if (writable) return RootVerdict.Deny("root 通道不允许把系统分区重挂载为可写")
        }

        val op = if (program in READ_ONLY_COMMANDS) RootOp.READ
        else WRITE_COMMANDS[program]
            ?: when {
                program in DELETE_COMMANDS -> RootOp.DELETE
                program in MOVE_COMMANDS -> RootOp.MOVE
                program in PERMISSION_COMMANDS -> RootOp.PERMISSION
                else -> null
            }
        // 未知命令不放行也不直接拒绝，交给用户确认。
        val effectiveOp = op ?: RootOp.OTHER

        val rawPaths = extractPaths(program, argv.drop(1), effectiveOp)
        if (rawPaths.isEmpty() && effectiveOp != RootOp.READ) {
            return RootVerdict.Ask(emptyList(), "命令未指定可判定的路径，需要你确认后执行")
        }

        val targets = mutableListOf<RootTarget>()
        for (raw in rawPaths) {
            val real = resolve(raw)
                ?: return RootVerdict.Deny("无法解析路径的实际位置：$raw")
            targets.add(RootTarget(real, effectiveOp))
        }

        // DENY 优先：任何目标落在禁区，整条命令阻断。
        for (target in targets) {
            if (target.op == RootOp.READ) continue
            if (target.realPath == "/") return RootVerdict.Deny("不允许修改根目录 /")
            for (prefix in SYSTEM_PREFIXES) {
                if (under(target.realPath, prefix)) {
                    return RootVerdict.Deny("$prefix 属于系统区域，root 通道只允许读取")
                }
            }
        }

        if (targets.all { it.op == RootOp.READ }) {
            return RootVerdict.Allow("只读命令")
        }

        val writable = targets.filter { it.op != RootOp.READ }
        if (writable.isEmpty()) return RootVerdict.Allow("只读命令")

        val allInWorkspace = writable.all { t -> WORKSPACE_PREFIXES.any { under(t.realPath, it) } }
        if (allInWorkspace && writable.all { it.op == RootOp.WRITE }) {
            return RootVerdict.Allow("工作区内的普通写入")
        }
        if (writable.any { t -> WORKSPACE_PREFIXES.any { under(t.realPath, it) } }) {
            return RootVerdict.Ask(writable, "工作区内删除/移动/改权限需要确认")
        }
        if (writable.any { under(it.realPath, SHARED_ROOT) }) {
            return RootVerdict.Ask(writable, "共享存储的写类操作需要你确认")
        }
        return RootVerdict.Ask(writable, "该位置不属于已知工作区，需要你确认")
    }

    /** 从参数里挑出路径操作数；flag 与命令自身的选项值都要跳过。 */
    private fun extractPaths(program: String, args: List<String>, op: RootOp): List<String> {
        val positional = mutableListOf<String>()
        var skipNext = false
        for (arg in args) {
            if (skipNext) {
                skipNext = false
                continue
            }
            if (arg.startsWith("-")) {
                // dd 的 if=/of= 之类用等号携带值，不需要额外跳过参数。
                if (arg in setOf("-o", "-t", "-m", "-u", "-g", "--owner", "--reference")) skipNext = true
                continue
            }
            if (arg.contains('=') && !arg.startsWith("/")) continue
            positional.add(arg)
        }
        return when (program) {
            "chmod", "chown", "chgrp" -> positional.drop(1)
            "cp", "ln" -> positional  // 源与目标都要判定
            "dd" -> args.filter { it.startsWith("if=") || it.startsWith("of=") }
                .map { it.substringAfter('=') }
                .filter { it.startsWith("/") }
            else -> positional
        }
    }

    private fun under(path: String, prefix: String): Boolean =
        path == prefix || path.startsWith("$prefix/")
}
