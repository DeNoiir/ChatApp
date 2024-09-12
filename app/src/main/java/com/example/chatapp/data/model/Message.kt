package com.example.chatapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 消息实体类
 * 表示数据库中的一条消息记录
 *
 * @property id 消息的唯一标识符
 * @property sender 发送者的用户ID
 * @property receiver 接收者的用户ID
 * @property type 消息类型(0表示文本,1表示文件)
 * @property content 消息内容
 * @property timestamp 消息发送的时间戳
 */
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val receiver: String,
    val type: Int,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)