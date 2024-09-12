package com.example.chatapp.data.dao

import androidx.room.*
import com.example.chatapp.data.model.Message
import kotlinx.coroutines.flow.Flow

/**
 * 消息数据访问对象接口
 * 定义了与消息相关的数据库操作
 */
@Dao
interface MessageDao {
    /**
     * 获取两个用户之间的所有消息
     *
     * @param userId1 第一个用户的ID
     * @param userId2 第二个用户的ID
     * @return 包含消息列表的Flow对象
     */
    @Query("SELECT * FROM messages WHERE (sender = :userId1 AND receiver = :userId2) OR (sender = :userId2 AND receiver = :userId1) ORDER BY timestamp ASC")
    fun getMessagesForUsers(userId1: String, userId2: String): Flow<List<Message>>

    /**
     * 插入一条新消息
     *
     * @param message 要插入的消息对象
     */
    @Insert
    suspend fun insertMessage(message: Message)

    /**
     * 删除两个用户之间的所有消息
     *
     * @param userId1 第一个用户的ID
     * @param userId2 第二个用户的ID
     */
    @Query("DELETE FROM messages WHERE (sender = :userId1 AND receiver = :userId2) OR (sender = :userId2 AND receiver = :userId1)")
    suspend fun deleteAllMessages(userId1: String, userId2: String)
}