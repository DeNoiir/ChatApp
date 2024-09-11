package com.example.chatapp.network

import android.util.Log
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

    private val discoveredUsers = mutableMapOf<String, Pair<String, String>>()

    suspend fun discoverDevices(currentUserId: String, currentUserName: String): List<Triple<String, String, String>> = withContext(Dispatchers.IO) {
        val foundDevices = mutableListOf<Triple<String, String, String>>()
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 5000

                val sendData = "$DISCOVERY_MESSAGE:$currentUserId:$currentUserName".toByteArray()
                val sendPacket = DatagramPacket(sendData, sendData.size,
                    InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)
                socket.send(sendPacket)

                val receiveData = ByteArray(BUFFER_SIZE)
                val receivePacket = DatagramPacket(receiveData, receiveData.size)

                while (true) {
                    try {
                        socket.receive(receivePacket)
                        val message = String(receivePacket.data, 0, receivePacket.length)
                        if (message.startsWith(DISCOVERY_RESPONSE_PREFIX)) {
                            val parts = message.split(":")
                            if (parts.size == 3 && parts[1] != currentUserId) {
                                val userId = parts[1]
                                val userName = parts[2]
                                val userIp = receivePacket.address.hostAddress
                                foundDevices.add(Triple(userId, userName, userIp) as Triple<String, String, String>)
                                discoveredUsers[userId] = Pair(userName, userIp) as Pair<String, String>
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ChatApp: UdpDiscoveryService", "Error receiving UDP packet: ${e.message}")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChatApp: UdpDiscoveryService", "Error in device discovery: ${e.message}")
        }
        foundDevices
    }

    @Synchronized
    fun startDiscoveryServer(currentUserId: String, currentUserName: String) {
        if (serverJob?.isActive == true) {
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
                        Log.e("ChatApp: UdpDiscoveryService", "Error in discovery server: ${e.message}")
                    }
                }
            } catch (e: BindException) {
                Log.e("ChatApp: UdpDiscoveryService", "Failed to start discovery server: ${e.message}")
            } catch (e: Exception) {
                Log.e("ChatApp: UdpDiscoveryService", "Unexpected error in discovery server: ${e.message}")
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
        Log.d("ChatApp: UdpDiscoveryService", "Discovery server stopped")
    }

    fun getUserIp(userId: String): String? {
        return discoveredUsers[userId]?.second
    }

    fun clearDiscoveredUsers() {
        discoveredUsers.clear()
    }
}