// UdpDiscoveryService.kt
package com.example.chatapp.network

import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.BindException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UdpDiscoveryService @Inject constructor() {
    private val DISCOVERY_PORT = 8888
    private val BUFFER_SIZE = 1024
    private val DISCOVERY_MESSAGE = "DISCOVER_CHATAPP_REQUEST"
    private val DISCOVERY_RESPONSE_PREFIX = "CHATAPP_RESPONSE:"

    private var serverJob: Job? = null
    private var serverSocket: DatagramSocket? = null

    suspend fun discoverDevices(currentUserId: String, currentUserName: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val foundDevices = mutableListOf<Pair<String, String>>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 5000 // 5 seconds timeout

            val sendData = "$DISCOVERY_MESSAGE:$currentUserId:$currentUserName".toByteArray()
            val sendPacket = DatagramPacket(sendData, sendData.size,
                InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)
            socket.send(sendPacket)

            val receiveData = ByteArray(BUFFER_SIZE)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)

            try {
                while (true) {
                    socket.receive(receivePacket)
                    val message = String(receivePacket.data, 0, receivePacket.length)
                    if (message.startsWith(DISCOVERY_RESPONSE_PREFIX)) {
                        val parts = message.split(":")
                        if (parts.size == 3 && parts[1] != currentUserId) {
                            foundDevices.add(Pair(parts[1], parts[2]))
                        }
                    }
                }
            } catch (e: Exception) {
                // Timeout or error occurred, discovery is complete
            }
        }
        foundDevices
    }

    @Synchronized
    fun startDiscoveryServer(currentUserId: String, currentUserName: String) {
        if (serverJob?.isActive == true) {
            // Server is already running
            return
        }

        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = DatagramSocket(DISCOVERY_PORT)
                val receiveData = ByteArray(BUFFER_SIZE)
                val receivePacket = DatagramPacket(receiveData, receiveData.size)

                while (isActive) {
                    try {
                        serverSocket?.receive(receivePacket)
                        val message = String(receivePacket.data, 0, receivePacket.length)
                        if (message.startsWith(DISCOVERY_MESSAGE)) {
                            val responseData = "$DISCOVERY_RESPONSE_PREFIX$currentUserId:$currentUserName".toByteArray()
                            val responsePacket = DatagramPacket(responseData, responseData.size,
                                receivePacket.address, receivePacket.port)
                            serverSocket?.send(responsePacket)
                        }
                    } catch (e: Exception) {
                        // Handle exceptions
                    }
                }
            } catch (e: BindException) {
                // Handle the case where the port is already in use
                println("Failed to start discovery server: ${e.message}")
            } finally {
                serverSocket?.close()
            }
        }
    }

    @Synchronized
    fun stopDiscoveryServer() {
        serverJob?.cancel()
        serverSocket?.close()
        serverSocket = null
    }
}