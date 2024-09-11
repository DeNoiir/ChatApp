package com.example.chatapp.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TcpCommunicationService @Inject constructor() {
    private val TCP_PORT = 9999
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var inputStream: DataInputStream? = null
    private var serverJob: Job? = null
    private var readJob: Job? = null

    private val _messageReceived = MutableSharedFlow<NetworkMessage>()
    val messageReceived = _messageReceived.asSharedFlow()

    private val _fileTransferProgress = MutableStateFlow<Float>(0f)
    val fileTransferProgress = _fileTransferProgress.asStateFlow()

    private val _chatEvent = MutableSharedFlow<ChatEvent>()
    val chatEvent = _chatEvent.asSharedFlow()

    fun startServer(onInvitationReceived: (String, String) -> Unit) {
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(TCP_PORT)
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    handleClient(socket, onInvitationReceived)
                }
            } catch (e: Exception) {
                Log.e("TcpCommunicationService", "Error in server: ${e.message}")
            }
        }
    }

    private suspend fun handleClient(socket: Socket, onInvitationReceived: (String, String) -> Unit) {
        withContext(Dispatchers.IO) {
            clientSocket = socket
            inputStream = DataInputStream(socket.getInputStream())
            outputStream = DataOutputStream(socket.getOutputStream())

            try {
                val messageType = inputStream?.readByte()
                if (messageType == MessageType.CHAT_INVITATION.value) {
                    val userId = inputStream?.readUTF()
                    val userName = inputStream?.readUTF()
                    if (userId != null && userName != null) {
                        onInvitationReceived(userId, userName)
                    }
                }
                startReading()
            } catch (e: Exception) {
                Log.e("TcpCommunicationService", "Error handling client: ${e.message}")
                closeConnection()
            }
        }
    }

    private fun startReading() {
        readJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                while (isActive) {
                    val messageType = inputStream?.readByte() ?: break
                    when (MessageType.fromByte(messageType)) {
                        MessageType.CHAT_MESSAGE -> {
                            val content = inputStream?.readUTF() ?: break
                            _messageReceived.emit(NetworkMessage.ChatMessage(content))
                        }
                        MessageType.FILE_TRANSFER_REQUEST -> {
                            val fileName = inputStream?.readUTF() ?: break
                            val fileSize = inputStream?.readLong() ?: break
                            _messageReceived.emit(NetworkMessage.FileTransferRequest(fileName, fileSize))
                        }
                        MessageType.FILE_TRANSFER_RESPONSE -> {
                            val accepted = inputStream?.readBoolean() ?: break
                            _messageReceived.emit(NetworkMessage.FileTransferResponse(accepted))
                        }
                        MessageType.FILE_DATA -> {
                            val fileSize = inputStream?.readLong() ?: break
                            _messageReceived.emit(NetworkMessage.FileData(fileSize, readFileData()))
                        }
                        MessageType.END_CHAT -> {
                            _chatEvent.emit(ChatEvent.ChatEnded)
                            break
                        }
                        MessageType.CHAT_INVITATION_RESPONSE -> {
                            val accepted = inputStream?.readBoolean() ?: break
                            _chatEvent.emit(ChatEvent.ChatInvitationResponse(accepted))
                        }
                        MessageType.FILE_TRANSFER_COMPLETED -> {
                            val fileName = inputStream?.readUTF() ?: break
                            _messageReceived.emit(NetworkMessage.FileTransferCompleted(fileName))
                        }
                        MessageType.FILE_RECEIVED_NOTIFICATION -> {
                            val fileName = inputStream?.readUTF() ?: break
                            _messageReceived.emit(NetworkMessage.FileReceivedNotification(fileName))
                        }
                        else -> break
                    }
                }
            } catch (e: Exception) {
                Log.e("TcpCommunicationService", "Error reading messages: ${e.message}")
            } finally {
                closeConnection()
            }
        }
    }

    private suspend fun readFileData(): ByteArray = withContext(Dispatchers.IO) {
        val baos = ByteArrayOutputStream()
        var totalBytesRead = 0L
        while (true) {
            val chunkSize = inputStream?.readInt() ?: break
            if (chunkSize <= 0) break // End of file transfer
            val buffer = ByteArray(chunkSize)
            var bytesRead = 0
            while (bytesRead < chunkSize) {
                val result = inputStream?.read(buffer, bytesRead, chunkSize - bytesRead) ?: -1
                if (result == -1) break
                bytesRead += result
            }
            baos.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead
            _fileTransferProgress.value = totalBytesRead.toFloat() / chunkSize
        }
        _fileTransferProgress.value = 1f
        baos.toByteArray()
    }

    suspend fun connectToUser(ipAddress: String, userId: String, userName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                clientSocket = Socket(ipAddress, TCP_PORT)
                outputStream = DataOutputStream(clientSocket?.getOutputStream())
                inputStream = DataInputStream(clientSocket?.getInputStream())
                sendMessage(MessageType.CHAT_INVITATION, userId, userName)
                startReading()
                true
            } catch (e: Exception) {
                Log.e("TcpCommunicationService", "Error connecting to user: ${e.message}")
                false
            }
        }
    }

    suspend fun sendMessage(type: MessageType, vararg params: Any) = withContext(Dispatchers.IO) {
        try {
            outputStream?.writeByte(type.value.toInt())
            when (type) {
                MessageType.CHAT_MESSAGE -> outputStream?.writeUTF(params[0] as String)
                MessageType.FILE_TRANSFER_REQUEST -> {
                    outputStream?.writeUTF(params[0] as String) // fileName
                    outputStream?.writeLong(params[1] as Long) // fileSize
                }
                MessageType.FILE_TRANSFER_RESPONSE -> outputStream?.writeBoolean(params[0] as Boolean)
                MessageType.FILE_DATA -> {
                    val fileSize = params[0] as Long
                    val inputStream = params[1] as InputStream
                    outputStream?.writeLong(fileSize)
                    var bytesWritten = 0L
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream?.writeInt(bytesRead)
                        outputStream?.write(buffer, 0, bytesRead)
                        bytesWritten += bytesRead
                        _fileTransferProgress.value = bytesWritten.toFloat() / fileSize
                    }
                    outputStream?.writeInt(0) // Signal end of file
                    _fileTransferProgress.value = 1f
                    // Send FILE_TRANSFER_COMPLETED message after file data
                    outputStream?.writeByte(MessageType.FILE_TRANSFER_COMPLETED.value.toInt())
                    outputStream?.writeUTF(params[2] as String) // fileName
                }
                MessageType.END_CHAT -> { /* No additional data needed */ }
                MessageType.CHAT_INVITATION -> {
                    outputStream?.writeUTF(params[0] as String) // userId
                    outputStream?.writeUTF(params[1] as String) // userName
                }
                MessageType.CHAT_INVITATION_RESPONSE -> outputStream?.writeBoolean(params[0] as Boolean)
                MessageType.FILE_TRANSFER_COMPLETED -> outputStream?.writeUTF(params[0] as String) // fileName
                MessageType.FILE_RECEIVED_NOTIFICATION -> outputStream?.writeUTF(params[0] as String) // fileName
            }
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e("TcpCommunicationService", "Error sending message: ${e.message}")
            throw e
        }
    }

    suspend fun closeConnection() = withContext(Dispatchers.IO) {
        serverJob?.cancel()
        readJob?.cancel()
        try {
            outputStream?.close()
            inputStream?.close()
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("TcpCommunicationService", "Error closing connection: ${e.message}")
        }
    }
}

sealed class ChatEvent {
    object ChatEnded : ChatEvent()
    data class ChatInvitationResponse(val accepted: Boolean) : ChatEvent()
}