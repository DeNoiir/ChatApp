package com.example.chatapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 用户实体类
 * 表示数据库中的一个用户记录
 *
 * @property id 用户的唯一标识符
 * @property name 用户名
 * @property password 用户密码(可为null)
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val password: String? = null
)