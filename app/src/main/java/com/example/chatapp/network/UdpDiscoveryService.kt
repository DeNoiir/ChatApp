package com.example.chatapp.network

import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UdpDiscoveryService @Inject constructor() {
    private val DISCOVERY_PORT = 8888
    private val BUFFER_SIZE = 1024
    private val DISCOVERY_MESSAGE = "DISCOVER_CHATAPP_REQUEST"
    private val DISCOVERY_RESPONSE = "DISCOVER_CHATAPP_RESPONSE"

    private var serverJob: Job? = null

    suspend fun discoverDevices(): List<String> = withContext(Dispatchers.IO) {
        val foundDevices = mutableListOf<String>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 5000 // 5 seconds timeout

            val sendData = DISCOVERY_MESSAGE.toByteArray()
            val sendPacket = DatagramPacket(sendData, sendData.size,
                InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)
            socket.send(sendPacket)

            val receiveData = ByteArray(BUFFER_SIZE)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)

            try {
                while (true) {
                    socket.receive(receivePacket)
                    val message = String(receivePacket.data, 0, receivePacket.length)
                    if (message.startsWith(DISCOVERY_RESPONSE)) {
                        foundDevices.add(receivePacket.address.hostAddress)
                    }
                }
            } catch (e: Exception) {
                // Timeout or error occurred, discovery is complete
            }
        }
        foundDevices
    }

    fun startDiscoveryServer() {
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            DatagramSocket(DISCOVERY_PORT).use { socket ->
                val receiveData = ByteArray(BUFFER_SIZE)
                val receivePacket = DatagramPacket(receiveData, receiveData.size)

                while (isActive) {
                    try {
                        socket.receive(receivePacket)
                        val message = String(receivePacket.data, 0, receivePacket.length)
                        if (message.startsWith(DISCOVERY_MESSAGE)) {
                            val responseData = DISCOVERY_RESPONSE.toByteArray()
                            val responsePacket = DatagramPacket(responseData, responseData.size,
                                receivePacket.address, receivePacket.port)
                            socket.send(responsePacket)
                        }
                    } catch (e: Exception) {
                        // Handle exceptions
                    }
                }
            }
        }
    }

    fun stopDiscoveryServer() {
        serverJob?.cancel()
    }
}