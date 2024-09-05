package com.example.chatapp.data.dao

import androidx.room.*
import com.example.chatapp.data.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sender = :userId OR receiver = :userId ORDER BY timestamp DESC")
    fun getMessagesForUser(userId: String): Flow<List<Message>>

    @Insert
    suspend fun insertMessage(message: Message)

    @Delete
    suspend fun deleteMessage(message: Message)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)
}