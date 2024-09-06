package com.example.chatapp.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.model.Message
import com.example.chatapp.data.repository.MessageRepository
import com.example.chatapp.data.repository.UserRepository
import com.example.chatapp.network.ConnectionState
import com.example.chatapp.network.TcpCommunicationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val tcpCommunicationService: TcpCommunicationService
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _otherUserName = MutableStateFlow<String>("")
    val otherUserName: StateFlow<String> = _otherUserName

    private val _filterType = MutableStateFlow(FilterType.ALL)
    val filterType: StateFlow<FilterType> = _filterType

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent

    val connectionState = tcpCommunicationService.connectionState

    private var currentUserId: String? = null
    private var otherUserId: String? = null

    private val _isNavigatingBack = MutableStateFlow(false)
    val isNavigatingBack: StateFlow<Boolean> = _isNavigatingBack.asStateFlow()

    private val _fileTransferEvent = MutableSharedFlow<FileTransferEvent>()
    val fileTransferEvent: SharedFlow<FileTransferEvent> = _fileTransferEvent

    init {
        viewModelScope.launch {
            tcpCommunicationService.messageReceived.collect { message ->
                currentUserId?.let { senderId ->
                    otherUserId?.let { receiverId ->
                        val newMessage = Message(sender = receiverId, receiver = senderId, content = message, type = 0)
                        messageRepository.insertMessage(newMessage)
                        loadMessages(senderId, receiverId)
                    }
                }
            }
        }

        viewModelScope.launch {
            tcpCommunicationService.connectionState.collect { state ->
                if (state == ConnectionState.Disconnected && !_isNavigatingBack.value) {
                    _isNavigatingBack.value = true
                    _navigationEvent.emit(NavigationEvent.NavigateBack)
                }
            }
        }
    }

    fun loadMessages(currentUserId: String, otherUserId: String) {
        this.currentUserId = currentUserId
        this.otherUserId = otherUserId
        viewModelScope.launch {
            messageRepository.getMessagesForUser(currentUserId, otherUserId).collect { messageList ->
                _messages.value = when (_filterType.value) {
                    FilterType.ALL -> messageList
                    FilterType.TEXT -> messageList.filter { it.type == 0 }
                    FilterType.FILE -> messageList.filter { it.type == 1 }
                }
            }
            _otherUserName.value = userRepository.getUserById(otherUserId)?.name ?: ""
        }
    }

    fun sendMessage(senderId: String, receiverId: String, content: String) {
        viewModelScope.launch {
            val message = Message(
                sender = senderId,
                receiver = receiverId,
                type = 0,
                content = content
            )
            messageRepository.insertMessage(message)
            tcpCommunicationService.sendMessage(content)
            loadMessages(senderId, receiverId)
        }
    }

    fun clearAllMessages(currentUserId: String, otherUserId: String) {
        viewModelScope.launch {
            messageRepository.deleteAllMessages(currentUserId, otherUserId)
            loadMessages(currentUserId, otherUserId)
        }
    }

    fun setFilterType(type: FilterType) {
        _filterType.value = type
        viewModelScope.launch {
            currentUserId?.let { senderId ->
                otherUserId?.let { receiverId ->
                    loadMessages(senderId, receiverId)
                }
            }
        }
    }

    fun closeChat() {
        viewModelScope.launch {
            if (!_isNavigatingBack.value) {
                _isNavigatingBack.value = true
                tcpCommunicationService.closeConnection()
                _navigationEvent.emit(NavigationEvent.NavigateBack)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        closeChat()
    }

    fun initiateFileTransfer(uri: Uri) {
        viewModelScope.launch {
            val fileName = uri.lastPathSegment ?: "unknown"
            tcpCommunicationService.sendMessage("FILE_TRANSFER_REQUEST:$fileName")
            _fileTransferEvent.emit(FileTransferEvent.InitiateTransfer(uri, fileName))
        }
    }
}

enum class FilterType {
    ALL, TEXT, FILE
}

sealed class NavigationEvent {
    object NavigateBack : NavigationEvent()
}

sealed class FileTransferEvent {
    data class InitiateTransfer(val uri: Uri, val fileName: String) : FileTransferEvent()
    data class ReceiveTransferRequest(val fileName: String) : FileTransferEvent()
}