package com.example.chatapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.model.Message
import com.example.chatapp.data.repository.MessageRepository
import com.example.chatapp.network.TcpCommunicationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val tcpCommunicationService: TcpCommunicationService
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    fun loadMessages(userId: String) {
        viewModelScope.launch {
            messageRepository.getMessagesForUser(userId).collect {
                _messages.value = it
            }
        }
    }

    fun sendMessage(senderId: String, receiverId: String, content: String) {
        viewModelScope.launch {
            val message = Message(
                sender = senderId,
                receiver = receiverId,
                type = 0, // 0 for text message
                content = content
            )
            messageRepository.insertMessage(message)
            tcpCommunicationService.sendMessage(receiverId, content)
        }
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            messageRepository.deleteMessage(message)
        }
    }
}