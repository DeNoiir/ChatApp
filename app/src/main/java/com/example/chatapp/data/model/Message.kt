package com.example.chatapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val receiver: String,
    val type: Int, // 0 for text, 1 for file
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)