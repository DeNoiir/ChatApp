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

/**
 * 聊天界面的 ViewModel
 * 负责处理聊天相关的业务逻辑，包括消息发送、接收、文件传输等
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val tcpCommunicationService: TcpCommunicationService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _otherUserName = MutableStateFlow("")
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
                    else -> { /* 处理其他事件（如果需要） */ }
                }
            }
        }

        viewModelScope.launch {
            tcpCommunicationService.fileTransferProgress.collect { progress ->
                when (val currentState = _fileTransferState.value) {
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

    /**
     * 初始化聊天
     *
     * @param currentUserId 当前用户ID
     * @param otherUserId 聊天对象的用户ID
     */
    fun initialize(currentUserId: String, otherUserId: String) {
        this.currentUserId = currentUserId
        this.otherUserId = otherUserId
        loadMessages(currentUserId, otherUserId)
        loadOtherUserName(otherUserId)
    }

    /**
     * 加载消息历史
     */
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

    /**
     * 加载聊天对象的用户名
     */
    private fun loadOtherUserName(otherUserId: String) {
        viewModelScope.launch {
            _otherUserName.value = userRepository.getUserById(otherUserId)?.name ?: ""
        }
    }

    /**
     * 发送文本消息
     */
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

    /**
     * 准备文件传输
     */
    fun prepareFileTransfer(uri: Uri) {
        viewModelScope.launch {
            try {
                val fileDetails = getFileDetails(uri)
                _fileTransferState.value = FileTransferState.AwaitingConfirmation(fileDetails.first, fileDetails.second, uri)
            } catch (e: Exception) {
                _fileTransferState.value = FileTransferState.Error("准备文件时出错：${e.message}")
            }
        }
    }

    /**
     * 确认文件传输
     */
    fun confirmFileTransfer() {
        viewModelScope.launch {
            val state = _fileTransferState.value
            if (state is FileTransferState.AwaitingConfirmation) {
                tcpCommunicationService.sendMessage(MessageType.FILE_TRANSFER_REQUEST, state.fileName, state.fileSize)
                _fileTransferState.value = FileTransferState.WaitingForAcceptance(state.fileName, state.uri)
            }
        }
    }

    /**
     * 取消文件传输
     */
    fun cancelFileTransfer() {
        _fileTransferState.value = FileTransferState.Idle
    }

    /**
     * 发送文件
     */
    private suspend fun sendFile(uri: Uri) {
        try {
            val fileDetails = getFileDetails(uri)
            _fileTransferState.value = FileTransferState.Sending(fileDetails.first)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tcpCommunicationService.sendMessage(MessageType.FILE_DATA, fileDetails.second, inputStream, fileDetails.first)
            }

            _fileTransferState.value = FileTransferState.Completed(fileDetails.first)

            // 将文件消息添加到本地数据库
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
            _fileTransferState.value = FileTransferState.Error("发送文件时出错：${e.message}")
        }
    }

    /**
     * 处理接收到的消息
     */
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
                receiveFile(message.data)
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
                // 此通知主要用于日志记录或额外操作（如果需要）
                // 主要的完成逻辑已在发送过程中处理
            }
        }
    }

    /**
     * 接受文件传输请求
     */
    fun acceptFileTransfer() {
        viewModelScope.launch {
            tcpCommunicationService.sendMessage(MessageType.FILE_TRANSFER_RESPONSE, true)
            val state = _fileTransferState.value
            if (state is FileTransferState.ReceivingRequest) {
                _fileTransferState.value = FileTransferState.Receiving(state.fileName)
            }
        }
    }

    /**
     * 拒绝文件传输请求
     */
    fun rejectFileTransfer() {
        viewModelScope.launch {
            tcpCommunicationService.sendMessage(MessageType.FILE_TRANSFER_RESPONSE, false)
            _fileTransferState.value = FileTransferState.Idle
        }
    }

    /**
     * 接收文件
     */
    private suspend fun receiveFile(data: ByteArray) {
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

                    // 发送文件接收通知
                    tcpCommunicationService.sendMessage(MessageType.FILE_RECEIVED_NOTIFICATION, fileName)
                }
            } catch (e: Exception) {
                _fileTransferState.value = FileTransferState.Error("接收文件时出错：${e.message}")
            }
        }
    }

    /**
     * 保存接收到的文件
     */
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
                _fileTransferState.value = FileTransferState.Error("保存文件时出错：${e.message}")
            }
        }
    }

    /**
     * 结束聊天
     */
    fun endChat() {
        viewModelScope.launch {
            if (!hasEndedChat) {
                hasEndedChat = true
                tcpCommunicationService.sendMessage(MessageType.END_CHAT)
                _chatState.value = ChatState.Ended
            }
        }
    }

    /**
     * 获取文件详情
     */
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
        throw IllegalStateException("无法获取文件详情")
    }

    /**
     * 重置文件传输状态
     */
    fun resetFileTransferState() {
        _fileTransferState.value = FileTransferState.Idle
    }

    /**
     * 设置消息过滤类型
     */
    fun setFilterType(type: FilterType) {
        _filterType.value = type
        loadMessages(currentUserId ?: return, otherUserId ?: return)
    }

    /**
     * 清除所有消息
     */
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

    /**
     * ViewModel 被清除时关闭连接
     */
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            tcpCommunicationService.closeConnection()
        }
    }
}

/**
 * 聊天状态
 */
sealed class ChatState {
    data object Active : ChatState()
    data object Ended : ChatState()
}

/**
 * 文件传输状态
 */
sealed class FileTransferState {
    /**
     * 空闲状态
     */
    data object Idle : FileTransferState()

    /**
     * 等待确认状态
     *
     * @property fileName 文件名
     * @property fileSize 文件大小
     * @property uri 文件URI
     */
    data class AwaitingConfirmation(val fileName: String, val fileSize: Long, val uri: Uri) : FileTransferState()

    /**
     * 等待接受状态
     *
     * @property fileName 文件名
     * @property uri 文件URI
     */
    data class WaitingForAcceptance(val fileName: String, val uri: Uri) : FileTransferState()

    /**
     * 发送中状态
     *
     * @property fileName 文件名
     * @property progress 发送进度
     */
    data class Sending(val fileName: String, val progress: Float = 0f) : FileTransferState()

    /**
     * 接收中状态
     *
     * @property fileName 文件名
     * @property progress 接收进度
     */
    data class Receiving(val fileName: String, val progress: Float = 0f) : FileTransferState()

    /**
     * 接收请求状态
     *
     * @property fileName 文件名
     * @property fileSize 文件大小
     */
    data class ReceivingRequest(val fileName: String, val fileSize: Long) : FileTransferState()

    /**
     * 完成状态
     *
     * @property fileName 文件名
     * @property tempFile 临时文件（可为空）
     */
    data class Completed(val fileName: String, val tempFile: File? = null) : FileTransferState()

    /**
     * 错误状态
     *
     * @property message 错误信息
     */
    data class Error(val message: String) : FileTransferState()
}

/**
 * 消息过滤类型
 */
enum class FilterType {
    /**
     * 所有消息
     */
    ALL,

    /**
     * 仅文本消息
     */
    TEXT,

    /**
     * 仅文件消息
     */
    FILE
}

/**
 * 文件保存事件
 *
 * @property tempFile 临时文件
 * @property fileName 文件名
 */
data class FileSaveEvent(val tempFile: File, val fileName: String)