package com.example.chatapp.data.repository

import com.example.chatapp.data.dao.MessageDao
import com.example.chatapp.data.model.Message
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MessageRepository @Inject constructor(private val messageDao: MessageDao) {
    fun getMessagesForUser(currentUserId: String, otherUserId: String): Flow<List<Message>> =
        messageDao.getMessagesForUsers(currentUserId, otherUserId)

    suspend fun insertMessage(message: Message) = messageDao.insertMessage(message)

    suspend fun deleteAllMessages(currentUserId: String, otherUserId: String) =
        messageDao.deleteAllMessages(currentUserId, otherUserId)
}