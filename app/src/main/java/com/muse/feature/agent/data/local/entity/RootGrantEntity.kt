package com.muse.feature.agent.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * root 通道的「始终允许」记录。
 *
 * 粒度是 realpath + 操作类型：允许写不等于允许删，
 * 所以同一路径的 write 与 delete 是两条独立记录。
 */
@Entity(
    tableName = "root_grants",
    indices = [Index(value = ["realPath", "operation"], unique = true)]
)
data class RootGrantEntity(
    @PrimaryKey val id: String,
    val realPath: String,
    val operation: String,
    val scope: String,
    val createdAt: Long,
    val revocable: Boolean = true
)
