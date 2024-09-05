package com.example.chatapp.data.repository

import com.example.chatapp.data.dao.MessageDao
import com.example.chatapp.data.model.Message
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MessageRepository @Inject constructor(private val messageDao: MessageDao) {
    fun getMessagesForUser(userId: String): Flow<List<Message>> = messageDao.getMessagesForUser(userId)

    suspend fun insertMessage(message: Message) = messageDao.insertMessage(message)

    suspend fun deleteMessage(message: Message) = messageDao.deleteMessage(message)

    suspend fun deleteMessageById(messageId: String) = messageDao.deleteMessageById(messageId)
}