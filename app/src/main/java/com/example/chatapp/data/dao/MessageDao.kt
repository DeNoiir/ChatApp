package com.example.chatapp.data.dao

import androidx.room.*
import com.example.chatapp.data.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE (sender = :userId1 AND receiver = :userId2) OR (sender = :userId2 AND receiver = :userId1) ORDER BY timestamp ASC")
    fun getMessagesForUsers(userId1: String, userId2: String): Flow<List<Message>>

    @Insert
    suspend fun insertMessage(message: Message)

    @Query("DELETE FROM messages WHERE (sender = :userId1 AND receiver = :userId2) OR (sender = :userId2 AND receiver = :userId1)")
    suspend fun deleteAllMessages(userId1: String, userId2: String)
}