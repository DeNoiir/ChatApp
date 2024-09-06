// TcpCommunicationService.kt
package com.example.chatapp.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TcpCommunicationService @Inject constructor() {
    private val TCP_PORT = 9999
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var serverJob: Job? = null
    private var readJob: Job? = null

    private val _messageReceived = MutableSharedFlow<String>()
    val messageReceived = _messageReceived.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    fun startServer(onInvitationReceived: (String) -> Unit) {
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(TCP_PORT)
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    handleClient(socket, onInvitationReceived)
                }
            } catch (e: Exception) {
                // Handle exceptions
            }
        }
    }

    private suspend fun handleClient(socket: Socket, onInvitationReceived: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            clientSocket = socket
            reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            writer = PrintWriter(socket.getOutputStream(), true)

            try {
                val firstMessage = reader?.readLine()
                if (firstMessage?.startsWith("INVITE:") == true) {
                    val userId = firstMessage.substringAfter("INVITE:")
                    onInvitationReceived(userId)
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
                    val message = reader?.readLine() ?: break
                    if (message == "DISCONNECT") {
                        break
                    }
                    _messageReceived.emit(message)
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
                writer = PrintWriter(clientSocket?.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(clientSocket?.getInputStream()))
                sendMessage("INVITE:$userId")
                _connectionState.value = ConnectionState.Connected
                startReading()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun sendMessage(message: String) = withContext(Dispatchers.IO) {
        writer?.println(message)
    }

    suspend fun closeConnection() = withContext(Dispatchers.IO) {
        try {
            // Send disconnect message if possible
            writer?.println("DISCONNECT")
        } catch (e: Exception) {
            // Ignore exceptions when sending disconnect message
        } finally {
            // Set connection state to disconnected immediately
            _connectionState.value = ConnectionState.Disconnected

            // Cancel jobs
            serverJob?.cancel()
            readJob?.cancel()

            // Close resources asynchronously
            launch {
                try {
                    writer?.close()
                    reader?.close()
                    clientSocket?.close()
                    serverSocket?.close()
                } catch (e: Exception) {
                    // Log or handle exceptions if necessary
                }
            }
        }
    }
}

enum class ConnectionState {
    Connected, Disconnected
}