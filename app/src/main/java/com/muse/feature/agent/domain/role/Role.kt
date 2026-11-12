package com.muse.feature.agent.domain.role

import kotlinx.serialization.Serializable

/**
 * 一个对话角色：名字、头像与「身份」提示词。
 *
 * 身份提示词就是原来提示词列表里的 `00-identity`，现在按角色各自维护，
 * 选中哪个角色，就用哪份身份去对话。
 */
@Serializable
data class Role(
    val id: String,
    val name: String,
    val identity: String = "",
    /** 自定义头像的绝对路径；为空表示用应用图标。 */
    val avatarPath: String? = null
)
