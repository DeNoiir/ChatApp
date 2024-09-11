package com.example.chatapp.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.model.Message
import com.example.chatapp.data.repository.MessageRepository
import com.example.chatapp.data.repository.UserRepository
import com.example.chatapp.network.ChatEvent
import com.example.chatapp.network.MessageType
import com.example.chatapp.network.NetworkMessage
import com.example.chatapp.network.TcpCommunicationService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val tcpCommunicationService: TcpCommunicationService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _otherUserName = MutableStateFlow<String>("")
    val otherUserName: StateFlow<String> = _otherUserName

    private val _chatState = MutableStateFlow<ChatState>(ChatState.Active)
    val chatState: StateFlow<ChatState> = _chatState

    private val _fileTransferState = MutableStateFlow<FileTransferState>(FileTransferState.Idle)
    val fileTransferState: StateFlow<FileTransferState> = _fileTransferState

    private val _filterType = MutableStateFlow(FilterType.ALL)
    val filterType: StateFlow<FilterType> = _filterType

    private val _fileSaveEvent = MutableSharedFlow<FileSaveEvent>()
    val fileSaveEvent = _fileSaveEvent.asSharedFlow()

    private var currentUserId: String? = null
    private var otherUserId: String? = null

    private var hasEndedChat = false

    init {
        viewModelScope.launch {
            tcpCommunicationService.messageReceived.collect { message ->
                handleReceivedMessage(message)
            }
        }

        viewModelScope.launch {
            tcpCommunicationService.chatEvent.collect { event ->
                when (event) {
                    is ChatEvent.ChatEnded -> {
                        if (!hasEndedChat) {
                            hasEndedChat = true
                            _chatState.value = ChatState.Ended
                        }
                    }
                    else -> { /* Handle other events if needed */ }
                }
            }
        }

        viewModelScope.launch {
            tcpCommunicationService.fileTransferProgress.collect { progress ->
                val currentState = _fileTransferState.value
                when (currentState) {
                    is FileTransferState.Sending -> {
                        _fileTransferState.value = currentState.copy(progress = progress)
                    }
                    is FileTransferState.Receiving -> {
                        _fileTransferState.value = currentState.copy(progress = progress)
                    }
                    else -> {}
                }
            }
        }
    }

    fun initialize(currentUserId: String, otherUserId: String) {
        this.currentUserId = currentUserId
        this.otherUserId = otherUserId
        loadMessages(currentUserId, otherUserId)
        loadOtherUserName(otherUserId)
    }

    private fun loadMessages(currentUserId: String, otherUserId: String) {
        viewModelScope.launch {
            messageRepository.getMessagesForUser(currentUserId, otherUserId).collect { messageList ->
                _messages.value = when (_filterType.value) {
                    FilterType.ALL -> messageList
                    FilterType.TEXT -> messageList.filter { it.type == 0 }
                    FilterType.FILE -> messageList.filter { it.type == 1 }
                }
            }
        }
    }

    private fun loadOtherUserName(otherUserId: String) {
        viewModelScope.launch {
            _otherUserName.value = userRepository.getUserById(otherUserId)?.name ?: ""
        }
    }

    fun sendMessage(content: String) {
        viewModelScope.launch {
            currentUserId?.let { senderId ->
                otherUserId?.let { receiverId ->
                    val message = Message(
                        sender = senderId,
                        receiver = receiverId,
                        type = 0,
                        content = content
                    )
                    messageRepository.insertMessage(message)
                    tcpCommunicationService.sendMessage(MessageType.CHAT_MESSAGE, content)
                }
            }
        }
    }

    fun prepareFileTransfer(uri: Uri) {
        viewModelScope.launch {
            try {
                val fileDetails = getFileDetails(uri)
                _fileTransferState.value = FileTransferState.AwaitingConfirmation(fileDetails.first, fileDetails.second, uri)
            } catch (e: Exception) {
                _fileTransferState.value = FileTransferState.Error("Error preparing file: ${e.message}")
            }
        }
    }

    fun confirmFileTransfer() {
        viewModelScope.launch {
            val state = _fileTransferState.value
            if (state is FileTransferState.AwaitingConfirmation) {
                tcpCommunicationService.sendMessage(MessageType.FILE_TRANSFER_REQUEST, state.fileName, state.fileSize)
                _fileTransferState.value = FileTransferState.WaitingForAcceptance(state.fileName, state.uri)
            }
        }
    }

    fun cancelFileTransfer() {
        _fileTransferState.value = FileTransferState.Idle
    }

    private suspend fun sendFile(uri: Uri) {
        try {
            val fileDetails = getFileDetails(uri)
            _fileTransferState.value = FileTransferState.Sending(fileDetails.first)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tcpCommunicationService.sendMessage(MessageType.FILE_DATA, fileDetails.second, inputStream, fileDetails.first)
            }

            _fileTransferState.value = FileTransferState.Completed(fileDetails.first)

            // Add file message to local database
            currentUserId?.let { senderId ->
                otherUserId?.let { receiverId ->
                    val newMessage = Message(
                        sender = senderId,
                        receiver = receiverId,
                        content = fileDetails.first,
                        type = 1
                    )
                    messageRepository.insertMessage(newMessage)
                }
            }

        } catch (e: Exception) {
            _fileTransferState.value = FileTransferState.Error("Error sending file: ${e.message}")
        }
    }

    private suspend fun handleReceivedMessage(message: NetworkMessage) {
        when (message) {
            is NetworkMessage.ChatMessage -> {
                currentUserId?.let { senderId ->
                    otherUserId?.let { receiverId ->
                        val newMessage = Message(
                            sender = receiverId,
                            receiver = senderId,
                            content = message.content,
                            type = 0
                        )
                        messageRepository.insertMessage(newMessage)
                    }
                }
            }
            is NetworkMessage.FileTransferRequest -> {
                _fileTransferState.value = FileTransferState.ReceivingRequest(message.fileName, message.fileSize)
            }
            is NetworkMessage.FileTransferResponse -> {
                if (message.accepted) {
                    val currentState = _fileTransferState.value
                    if (currentState is FileTransferState.WaitingForAcceptance) {
                        sendFile(currentState.uri)
                    }
                } else {
                    _fileTransferState.value = FileTransferState.Idle
                }
            }
            is NetworkMessage.FileData -> {
                receiveFile(message.fileSize, message.data)
            }
            is NetworkMessage.FileTransferCompleted -> {
                _fileTransferState.value = FileTransferState.Completed(message.fileName)
                currentUserId?.let { senderId ->
                    otherUserId?.let { receiverId ->
                        val newMessage = Message(
                            sender = receiverId,
                            receiver = senderId,
                            content = message.fileName,
                            type = 1
                        )
                        messageRepository.insertMessage(newMessage)
                    }
                }
            }
            is NetworkMessage.FileReceivedNotification -> {
                // This notification is now mainly for logging or additional actions if needed
                // The main completion logic is already handled in the sending process
            }
        }
    }

    fun acceptFileTransfer() {
        viewModelScope.launch {
            tcpCommunicationService.sendMessage(MessageType.FILE_TRANSFER_RESPONSE, true)
            val state = _fileTransferState.value
            if (state is FileTransferState.ReceivingRequest) {
                _fileTransferState.value = FileTransferState.Receiving(state.fileName)
            }
        }
    }

    fun rejectFileTransfer() {
        viewModelScope.launch {
            tcpCommunicationService.sendMessage(MessageType.FILE_TRANSFER_RESPONSE, false)
            _fileTransferState.value = FileTransferState.Idle
        }
    }

    private suspend fun receiveFile(fileSize: Long, data: ByteArray) {
        withContext(Dispatchers.IO) {
            try {
                val state = _fileTransferState.value
                if (state is FileTransferState.Receiving) {
                    val fileName = state.fileName
                    val tempFile = File(context.cacheDir, fileName)
                    FileOutputStream(tempFile).use { outputStream ->
                        outputStream.write(data)
                    }
                    _fileTransferState.value = FileTransferState.Completed(fileName, tempFile)
                    _fileSaveEvent.emit(FileSaveEvent(tempFile, fileName))

                    // Send FILE_RECEIVED_NOTIFICATION
                    tcpCommunicationService.sendMessage(MessageType.FILE_RECEIVED_NOTIFICATION, fileName)
                }
            } catch (e: Exception) {
                _fileTransferState.value = FileTransferState.Error("Error receiving file: ${e.message}")
            }
        }
    }

    fun saveFile(tempFile: File, destinationUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                    tempFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                tempFile.delete()
                _fileTransferState.value = FileTransferState.Completed(tempFile.name)

                currentUserId?.let { senderId ->
                    otherUserId?.let { receiverId ->
                        val newMessage = Message(
                            sender = receiverId,
                            receiver = senderId,
                            content = tempFile.name,
                            type = 1
                        )
                        messageRepository.insertMessage(newMessage)
                    }
                }
            } catch (e: Exception) {
                _fileTransferState.value = FileTransferState.Error("Error saving file: ${e.message}")
            }
        }
    }

    fun endChat() {
        viewModelScope.launch {
            if (!hasEndedChat) {
                hasEndedChat = true
                tcpCommunicationService.sendMessage(MessageType.END_CHAT)
                _chatState.value = ChatState.Ended
            }
        }
    }

    private fun getFileDetails(uri: Uri): Pair<String, Long> {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            it.moveToFirst()
            val name = it.getString(nameIndex)
            val size = it.getLong(sizeIndex)
            return Pair(name, size)
        }
        throw IllegalStateException("Unable to get file details")
    }

    fun resetFileTransferState() {
        _fileTransferState.value = FileTransferState.Idle
    }

    fun setFilterType(type: FilterType) {
        _filterType.value = type
        loadMessages(currentUserId ?: return, otherUserId ?: return)
    }

    fun clearAllMessages() {
        viewModelScope.launch {
            currentUserId?.let { senderId ->
                otherUserId?.let { receiverId ->
                    messageRepository.deleteAllMessages(senderId, receiverId)
                    loadMessages(senderId, receiverId)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            tcpCommunicationService.closeConnection()
        }
    }
}

sealed class ChatState {
    object Active : ChatState()
    object Ended : ChatState()
}

sealed class FileTransferState {
    object Idle : FileTransferState()
    data class AwaitingConfirmation(val fileName: String, val fileSize: Long, val uri: Uri) : FileTransferState()
    data class WaitingForAcceptance(val fileName: String, val uri: Uri) : FileTransferState()
    data class Sending(val fileName: String, val progress: Float = 0f) : FileTransferState()
    data class Receiving(val fileName: String, val progress: Float = 0f) : FileTransferState()
    data class ReceivingRequest(val fileName: String, val fileSize: Long) : FileTransferState()
    data class Completed(val fileName: String, val tempFile: File? = null) : FileTransferState()
    data class Error(val message: String) : FileTransferState()
}

enum class FilterType {
    ALL, TEXT, FILE
}

data class FileSaveEvent(val tempFile: File, val fileName: String)