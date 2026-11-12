package com.muse.feature.agent.domain.role

import android.content.Context
import com.muse.core.util.FileLogger
import com.muse.feature.agent.domain.container.ContainerInstaller
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class RoleIndex(
    val roles: List<Role> = emptyList(),
    val currentId: String? = null
)

/**
 * 角色的存取。
 *
 * 存 `~/.muse/roles/index.json`，头像单独落 `~/.muse/roles/<id>.avatar`，
 * 不把图片塞进 JSON，避免索引文件随头像变大。
 */
@Singleton
class RoleRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val containerInstaller: ContainerInstaller
) {
    private companion object {
        const val TAG = "RoleRepository"
        const val DEFAULT_ID = "muse"
        const val DEFAULT_NAME = "Muse"
        const val BUILTIN_IDENTITY_ASSET = "prompts/00-identity.md"
        const val MAX_AVATAR_BYTES = 8L * 1024 * 1024
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val dir: File get() = File(containerInstaller.museDir, "roles")
    private val indexFile: File get() = File(dir, "index.json")

    private fun readIndex(): RoleIndex {
        if (!indexFile.isFile) return RoleIndex()
        return runCatching {
            json.decodeFromString<RoleIndex>(indexFile.readText(Charsets.UTF_8))
        }.getOrElse {
            FileLogger.w(TAG, "角色索引解析失败，回退空索引: ${it.message}")
            RoleIndex()
        }
    }

    private fun writeIndex(index: RoleIndex) {
        dir.mkdirs()
        indexFile.writeText(json.encodeToString(index), Charsets.UTF_8)
    }

    /** 首次使用时用内置 00-identity 作为默认角色 Muse 的身份。 */
    private fun builtinIdentity(): String = runCatching {
        context.assets.open(BUILTIN_IDENTITY_ASSET).bufferedReader().use { it.readText() }
    }.getOrDefault("")

    @Synchronized
    fun list(): List<Role> {
        val index = readIndex()
        if (index.roles.isNotEmpty()) return index.roles
        val seeded = listOf(Role(id = DEFAULT_ID, name = DEFAULT_NAME, identity = builtinIdentity()))
        writeIndex(RoleIndex(roles = seeded, currentId = seeded.first().id))
        return seeded
    }

    @Synchronized
    fun current(): Role {
        val index = readIndex()
        val roles = if (index.roles.isEmpty()) list() else index.roles
        return roles.firstOrNull { it.id == index.currentId } ?: roles.first()
    }

    @Synchronized
    fun setCurrent(id: String) {
        val index = readIndex()
        if (index.roles.none { it.id == id }) return
        writeIndex(index.copy(currentId = id))
    }

    @Synchronized
    fun upsert(role: Role) {
        val index = readIndex()
        val roles = index.roles.toMutableList()
        val at = roles.indexOfFirst { it.id == role.id }
        if (at >= 0) roles[at] = role else roles.add(role)
        writeIndex(index.copy(roles = roles))
    }

    /** 删除角色；最后一个角色不允许删除。 */
    @Synchronized
    fun delete(id: String): Boolean {
        val index = readIndex()
        if (index.roles.size <= 1) return false
        val remaining = index.roles.filterNot { it.id == id }
        if (remaining.size == index.roles.size) return false
        val nextCurrent = if (index.currentId == id) remaining.first().id else index.currentId
        writeIndex(RoleIndex(roles = remaining, currentId = nextCurrent))
        avatarFile(id).takeIf { it.isFile }?.delete()
        return true
    }

    fun newRole(name: String): Role = Role(id = UUID.randomUUID().toString(), name = name)

    fun avatarFile(id: String): File = File(dir, "$id.avatar")

    /**
     * 保存头像并同时更新角色记录。
     *
     * 文件名带时间戳：同一个角色反复换头像时路径会变，UI 侧的图片缓存才会失效重载。
     * 固定文件名会让界面上一直显示第一张图。
     */
    @Synchronized
    fun updateAvatar(id: String, bytes: ByteArray): String? = runCatching {
        require(bytes.isNotEmpty()) { "头像为空" }
        require(bytes.size <= MAX_AVATAR_BYTES) { "头像文件过大" }
        dir.mkdirs()
        val role = list().firstOrNull { it.id == id } ?: return null
        val target = File(dir, "${id}_${System.currentTimeMillis()}.avatar")
        target.writeBytes(bytes)
        upsert(role.copy(avatarPath = target.absolutePath))
        role.avatarPath?.takeIf { it != target.absolutePath }?.let { File(it).delete() }
        target.absolutePath
    }.getOrElse {
        FileLogger.w(TAG, "头像保存失败: ${it.message}")
        null
    }

    fun removeAvatar(id: String) {
        val role = list().firstOrNull { it.id == id } ?: return
        role.avatarPath?.let { File(it).delete() }
        upsert(role.copy(avatarPath = null))
    }

    /** 应用图标作为默认头像的等价物：返回图标的解码结果供 UI 回退。 */
    fun appIconDrawable(): android.graphics.drawable.Drawable? = runCatching {
        context.packageManager.getApplicationIcon(context.packageName)
    }.getOrNull()

    fun avatarBytes(id: String): ByteArray? =
        avatarFile(id).takeIf { it.isFile }?.readBytes()
}
