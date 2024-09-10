package com.example.chatapp.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private val _messageReceived = MutableSharedFlow<Pair<Byte, String>>()
    val messageReceived = _messageReceived.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _fileTransferProgress = MutableStateFlow<Float>(0f)
    val fileTransferProgress = _fileTransferProgress.asStateFlow()

    fun startServer(onInvitationReceived: (String) -> Unit) {
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

    private suspend fun handleClient(socket: Socket, onInvitationReceived: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            clientSocket = socket
            inputStream = DataInputStream(socket.getInputStream())
            outputStream = DataOutputStream(socket.getOutputStream())

            try {
                val messageType = inputStream?.readByte()
                if (messageType == 0x01.toByte()) {
                    val userId = inputStream?.readUTF()
                    if (userId != null) {
                        onInvitationReceived(userId)
                    }
                }

                _connectionState.value = ConnectionState.Connected
                startReading()

            } catch (e: Exception) {
                closeConnection()
            }
        }
    }

    private fun startReading() {
        readJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                while (isActive) {
                    val messageType = inputStream?.readByte() ?: break
                    val message = inputStream?.readUTF() ?: break
                    if (messageType == 0x09.toByte()) {
                        break
                    }
                    _messageReceived.emit(Pair(messageType, message))
                }
            } finally {
                closeConnection()
            }
        }
    }

    suspend fun connectToUser(ipAddress: String, userId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                clientSocket = Socket(ipAddress, TCP_PORT)
                outputStream = DataOutputStream(clientSocket?.getOutputStream())
                inputStream = DataInputStream(clientSocket?.getInputStream())
                sendMessage(0x01.toByte(), userId)
                _connectionState.value = ConnectionState.Connected
                startReading()
                true
            } catch (e: Exception) {
                Log.e("TcpCommunicationService", "Error connecting to user: ${e.message}")
                false
            }
        }
    }

    suspend fun sendMessage(type: Byte, message: String) = withContext(Dispatchers.IO) {
        outputStream?.writeByte(type.toInt())
        outputStream?.writeUTF(message)
        outputStream?.flush()
    }

    suspend fun sendFileSize(fileSize: Long) = withContext(Dispatchers.IO) {
        try {
            Log.d("TcpCommunicationService", "Sending file size: $fileSize bytes")
            sendMessage(0x0B.toByte(), fileSize.toString())
            Log.d("TcpCommunicationService", "File size sent: $fileSize")
        } catch (e: Exception) {
            Log.e("TcpCommunicationService", "Error sending file size: ${e.message}")
            throw e
        }
    }

    suspend fun sendFileData(inputStream: InputStream, fileSize: Long) = withContext(Dispatchers.IO) {
        try {
            Log.d("TcpCommunicationService", "Starting to send file data, size: $fileSize bytes")

            var totalBytesSent: Long = 0
            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream?.write(buffer, 0, bytesRead)
                totalBytesSent += bytesRead
                _fileTransferProgress.value = totalBytesSent.toFloat() / fileSize
                Log.d("TcpCommunicationService", "Sent $totalBytesSent bytes out of $fileSize")
            }

            outputStream?.flush()
            Log.d("TcpCommunicationService", "File send complete. Total sent: $totalBytesSent")
        } catch (e: Exception) {
            Log.e("TcpCommunicationService", "Error sending file data: ${e.message}")
            throw e
        } finally {
            _fileTransferProgress.value = 0f
        }
    }

    suspend fun receiveFileSize(): Long = withContext(Dispatchers.IO) {
        try {
            val (_, fileSizeString) = _messageReceived.first { (type, _) -> type == 0x0B.toByte() }
            val fileSize = fileSizeString.toLong()
            Log.d("TcpCommunicationService", "Received file size: $fileSize bytes")
            return@withContext fileSize
        } catch (e: Exception) {
            Log.e("TcpCommunicationService", "Error receiving file size: ${e.message}")
            throw e
        }
    }

    suspend fun receiveFileData(tempFile: File, fileSize: Long) = withContext(Dispatchers.IO) {
        try {
            Log.d("TcpCommunicationService", "Starting to receive file data, expected size: $fileSize bytes")

            var totalBytesReceived: Long = 0
            val buffer = ByteArray(8192)
            var bytesRead: Int

            FileOutputStream(tempFile).use { outputStream ->
                while (totalBytesReceived < fileSize) {
                    bytesRead = inputStream?.read(buffer, 0, minOf(buffer.size, (fileSize - totalBytesReceived).toInt())) ?: -1
                    if (bytesRead == -1) break
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesReceived += bytesRead
                    _fileTransferProgress.value = totalBytesReceived.toFloat() / fileSize
                    Log.d("TcpCommunicationService", "Received $totalBytesReceived bytes out of $fileSize")
                }
            }

            if (totalBytesReceived == fileSize) {
                Log.d("TcpCommunicationService", "File receive complete. Total received: $totalBytesReceived")
            } else {
                throw IOException("File transfer incomplete. Received $totalBytesReceived out of $fileSize bytes")
            }
        } catch (e: Exception) {
            Log.e("TcpCommunicationService", "Error receiving file data: ${e.message}")
            throw e
        } finally {
            _fileTransferProgress.value = 0f
        }
    }

    suspend fun closeConnection() = withContext(Dispatchers.IO) {
        try {
            sendMessage(0x09.toByte(), "DISCONNECT")
        } catch (e: Exception) {
            // Ignore exceptions when sending disconnect message
        } finally {
            _connectionState.value = ConnectionState.Disconnected

            serverJob?.cancel()
            readJob?.cancel()

            launch {
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
    }
}

enum class ConnectionState {
    Connected, Disconnected
}