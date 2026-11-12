package com.muse.feature.agent.domain.root

/**
 * 把模型给的命令行拆成 argv，并在拆开之前拒绝一切 shell 语法。
 *
 * 安全性来自这里：命令最终交给 `su -c <string>`，而 su 会再用 shell 解释一遍。
 * 只要保证 token 里不含元字符，并且拼回去时逐字引用，`;`、`&&`、`$()` 就没有机会生效。
 * 与其在拼接后做转义（容易漏），不如在入口直接拒绝这些构造。
 */
object RootTokenizer {

    private val SHELL_METACHARACTERS = setOf(
        ';', '|', '&', '>', '<', '$', '`', '(', ')', '{', '}', '\n', '\r', '\\'
    )

    sealed interface Result {
        data class Ok(val argv: List<String>) : Result
        data class Rejected(val reason: String) : Result
    }

    fun tokenize(command: String): Result {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return Result.Rejected("命令为空")

        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var started = false

        for (ch in trimmed) {
            when {
                quote != null -> {
                    if (ch == quote) quote = null else current.append(ch)
                }
                ch == '\'' || ch == '"' -> {
                    quote = ch
                    started = true
                }
                ch.isWhitespace() -> {
                    if (started) {
                        tokens.add(current.toString())
                        current.setLength(0)
                        started = false
                    }
                }
                ch in SHELL_METACHARACTERS -> {
                    return Result.Rejected(
                        "命令包含 shell 元字符 '$ch'：root 通道只接受单条命令，" +
                            "不支持管道、重定向、命令替换或串联，请拆成多次调用"
                    )
                }
                else -> {
                    current.append(ch)
                    started = true
                }
            }
        }

        if (quote != null) return Result.Rejected("引号未闭合")
        if (started) tokens.add(current.toString())
        if (tokens.isEmpty()) return Result.Rejected("命令为空")
        return Result.Ok(tokens)
    }

    /** 逐字引用单个 token，拼回 `su -c` 需要的字符串时使用。 */
    fun quote(token: String): String {
        if (token.isEmpty()) return "''"
        val safe = token.all { it.isLetterOrDigit() || it in "._-/@%+=:,^" }
        if (safe) return token
        return "'" + token.replace("'", "'\\''") + "'"
    }
}
