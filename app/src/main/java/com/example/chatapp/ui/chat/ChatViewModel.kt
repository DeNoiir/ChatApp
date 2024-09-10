package com.example.chatapp.ui.chat

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.model.Message
import com.example.chatapp.data.repository.MessageRepository
import com.example.chatapp.data.repository.UserRepository
import com.example.chatapp.network.ConnectionState
import com.example.chatapp.network.TcpCommunicationService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
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

    private val _filterType = MutableStateFlow(FilterType.ALL)
    val filterType: StateFlow<FilterType> = _filterType

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent

    val connectionState = tcpCommunicationService.connectionState

    val fileTransferProgress = tcpCommunicationService.fileTransferProgress

    private var currentUserId: String? = null
    private var otherUserId: String? = null
    private var otherUserIp: String? = null

    private val _isNavigatingBack = MutableStateFlow(false)
    val isNavigatingBack: StateFlow<Boolean> = _isNavigatingBack.asStateFlow()

    private val _fileEvent = MutableSharedFlow<FileEvent>()
    val fileEvent: SharedFlow<FileEvent> = _fileEvent

    init {
        viewModelScope.launch {
            tcpCommunicationService.messageReceived.collect { (type, content) ->
                currentUserId?.let { senderId ->
                    otherUserId?.let { receiverId ->
                        when (type) {
                            0x03.toByte() -> { // Chat message
                                val newMessage = Message(sender = receiverId, receiver = senderId, content = content, type = 0)
                                messageRepository.insertMessage(newMessage)
                                loadMessages(senderId, receiverId)
                            }
                            0x04.toByte() -> { // File transfer request
                                val (fileName, fileSize) = content.split(":")
                                handleFileTransferRequest(fileName, fileSize.toLong())
                            }
                            0x07.toByte() -> { // File transfer complete
                                val newMessage = Message(sender = receiverId, receiver = senderId, content = content, type = 1)
                                messageRepository.insertMessage(newMessage)
                                loadMessages(senderId, receiverId)
                            }
                        }
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

    fun initialize(currentUserId: String, otherUserId: String, otherUserIp: String) {
        this.currentUserId = currentUserId
        this.otherUserId = otherUserId
        this.otherUserIp = otherUserIp
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
                    tcpCommunicationService.sendMessage(0x03.toByte(), content)
                    loadMessages(senderId, receiverId)
                }
            }
        }
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

    fun sendFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val fileDetails = getFileDetails(uri)
                Log.d("ChatViewModel", "Sending file: ${fileDetails.first}, size: ${fileDetails.second}")
                tcpCommunicationService.sendMessage(0x04.toByte(), "${fileDetails.first}:${fileDetails.second}")
                _fileEvent.emit(FileEvent.SendingFile(fileDetails.first))

                // Send file size
                tcpCommunicationService.sendFileSize(fileDetails.second)

                // Wait for 5 seconds
                delay(5000)

                // Send file data
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    tcpCommunicationService.sendFileData(inputStream, fileDetails.second)
                }

                Log.d("ChatViewModel", "File sent successfully: ${fileDetails.first}")
                _fileEvent.emit(FileEvent.FileSent(fileDetails.first))

                currentUserId?.let { senderId ->
                    otherUserId?.let { receiverId ->
                        val message = Message(
                            sender = senderId,
                            receiver = receiverId,
                            type = 1,
                            content = fileDetails.first
                        )
                        messageRepository.insertMessage(message)
                        loadMessages(senderId, receiverId)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error sending file: ${e.message}")
                _fileEvent.emit(FileEvent.FileError("Error sending file: ${e.message}"))
            }
        }
    }

    private suspend fun handleFileTransferRequest(fileName: String, fileSize: Long) {
        _fileEvent.emit(FileEvent.ReceivingFile(fileName, fileSize))
        try {
            tcpCommunicationService.sendMessage(0x05.toByte(), "ACCEPTED")

            // Receive file size
            val receivedFileSize = tcpCommunicationService.receiveFileSize()
            if (receivedFileSize != fileSize) {
                throw IOException("Received file size ($receivedFileSize) does not match expected size ($fileSize)")
            }

            // Receive file data
            val tempFile = File(context.getExternalFilesDir(null), fileName)
            tcpCommunicationService.receiveFileData(tempFile, fileSize)

            // Move file to Downloads folder
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destinationFile = File(downloadsDir, fileName)

            tempFile.inputStream().use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Delete the temp file
            tempFile.delete()

            Log.d("ChatViewModel", "File moved to Downloads: ${destinationFile.absolutePath}")

            currentUserId?.let { senderId ->
                otherUserId?.let { receiverId ->
                    val message = Message(
                        sender = receiverId,
                        receiver = senderId,
                        type = 1,
                        content = fileName
                    )
                    messageRepository.insertMessage(message)
                    loadMessages(senderId, receiverId)
                }
            }
            _fileEvent.emit(FileEvent.FileReceived(fileName))
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error receiving file: ${e.message}")
            _fileEvent.emit(FileEvent.FileError("Error receiving file: ${e.message}"))
        }
    }

    private fun getFileDetails(uri: Uri): Pair<String, Long> {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
            it.moveToFirst()
            val name = it.getString(nameIndex)
            val size = it.getLong(sizeIndex)
            return Pair(name, size)
        }
        throw IllegalStateException("Unable to get file details")
    }

    override fun onCleared() {
        super.onCleared()
        closeChat()
    }
}

enum class FilterType {
    ALL, TEXT, FILE
}

sealed class NavigationEvent {
    object NavigateBack : NavigationEvent()
}

sealed class FileEvent {
    data class SendingFile(val fileName: String) : FileEvent()
    data class FileSent(val fileName: String) : FileEvent()
    data class ReceivingFile(val fileName: String, val fileSize: Long) : FileEvent()
    data class FileReceived(val fileName: String) : FileEvent()
    data class FileError(val error: String) : FileEvent()
}