package com.example.chatapp.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class TcpCommunicationService {
    private val TCP_PORT = 9999

    suspend fun startServer(onMessageReceived: (String) -> Unit) = withContext(Dispatchers.IO) {
        val serverSocket = ServerSocket(TCP_PORT)
        while (true) {
            val clientSocket = serverSocket.accept()
            handleClient(clientSocket, onMessageReceived)
        }
    }

    private suspend fun handleClient(clientSocket: Socket, onMessageReceived: (String) -> Unit) = withContext(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
        val writer = PrintWriter(clientSocket.getOutputStream(), true)

        try {
            var message: String?
            while (reader.readLine().also { message = it } != null) {
                onMessageReceived(message ?: "")
            }
        } finally {
            clientSocket.close()
        }
    }

    suspend fun sendMessage(ipAddress: String, message: String) = withContext(Dispatchers.IO) {
        Socket(ipAddress, TCP_PORT).use { socket ->
            val writer = PrintWriter(socket.getOutputStream(), true)
            writer.println(message)
        }
    }
}