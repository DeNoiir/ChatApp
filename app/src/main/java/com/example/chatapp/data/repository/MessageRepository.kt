package com.example.chatapp.data.repository

import com.example.chatapp.data.dao.MessageDao
import com.example.chatapp.data.model.Message
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 消息数据仓库类
 * 负责处理与消息相关的数据操作
 *
 * @property messageDao 消息数据访问对象
 */
class MessageRepository @Inject constructor(private val messageDao: MessageDao) {

    /**
     * 获取两个用户之间的所有消息
     *
     * @param currentUserId 当前用户ID
     * @param otherUserId 其他用户ID
     * @return 包含两用户间所有消息的Flow对象
     */
    fun getMessagesForUser(currentUserId: String, otherUserId: String): Flow<List<Message>> =
        messageDao.getMessagesForUsers(currentUserId, otherUserId)

    /**
     * 插入新消息
     *
     * @param message 要插入的消息对象
     */
    suspend fun insertMessage(message: Message) = messageDao.insertMessage(message)

    /**
     * 删除两个用户之间的所有消息
     *
     * @param currentUserId 当前用户ID
     * @param otherUserId 其他用户ID
     */
    suspend fun deleteAllMessages(currentUserId: String, otherUserId: String) =
        messageDao.deleteAllMessages(currentUserId, otherUserId)
}