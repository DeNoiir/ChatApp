package com.example.chatapp.ui.users

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.model.User
import com.example.chatapp.data.repository.UserRepository
import com.example.chatapp.network.TcpCommunicationService
import com.example.chatapp.network.UdpDiscoveryService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val udpDiscoveryService: UdpDiscoveryService,
    private val tcpCommunicationService: TcpCommunicationService
) : ViewModel() {
    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    private val _discoveredUsers = MutableStateFlow<List<User>>(emptyList())
    val discoveredUsers: StateFlow<List<User>> = _discoveredUsers

    private val _chatInvitation = MutableStateFlow<Pair<String, String>?>(null)
    val chatInvitation: StateFlow<Pair<String, String>?> = _chatInvitation

    private lateinit var currentUser: User

    fun initialize(userId: String) {
        viewModelScope.launch {
            try {
                currentUser = userRepository.getUserById(userId) ?: throw Exception("User not found")
                loadUsers()
                startDiscoveryServer()
                startTcpServer()
            } catch (e: Exception) {
                Log.e("ChatApp: UsersViewModel", "Error initializing: ${e.message}")
            }
        }
    }

    private fun loadUsers() {
        viewModelScope.launch {
            try {
                userRepository.getAllUsers().collect { userList ->
                    _users.value = userList.filter { it.id != currentUser.id }
                }
            } catch (e: Exception) {
                Log.e("ChatApp: UsersViewModel", "Error loading users: ${e.message}")
            }
        }
    }

    fun discoverDevices() {
        viewModelScope.launch {
            try {
                val discoveredDevices = udpDiscoveryService.discoverDevices(currentUser.id, currentUser.name)
                _discoveredUsers.value = discoveredDevices.map { (id, name, _) ->
                    User(id = id, name = name)
                }
                // Update the database with discovered users
                discoveredDevices.forEach { (id, name, _) ->
                    userRepository.insertOrUpdateUser(id, name)
                }
            } catch (e: Exception) {
                Log.e("ChatApp: UsersViewModel", "Error discovering devices: ${e.message}")
            }
        }
    }

    private fun startDiscoveryServer() {
        udpDiscoveryService.startDiscoveryServer(currentUser.id, currentUser.name)
        Log.d("ChatApp: UsersViewModel", "Discovery server started")
    }

    private fun startTcpServer() {
        tcpCommunicationService.startServer { userId ->
            viewModelScope.launch {
                try {
                    val user = userRepository.getUserById(userId)
                    if (user != null) {
                        _chatInvitation.value = Pair(userId, user.name)
                    }
                } catch (e: Exception) {
                    Log.e("ChatApp: UsersViewModel", "Error handling chat invitation: ${e.message}")
                }
            }
        }
        Log.d("ChatApp: UsersViewModel", "TCP server started")
    }

    suspend fun connectToUser(userId: String): Boolean {
        return try {
            val ip = udpDiscoveryService.getUserIp(userId) ?: throw Exception("IP address not found for user")
            tcpCommunicationService.connectToUser(ip, currentUser.id)
        } catch (e: Exception) {
            Log.e("ChatApp: UsersViewModel", "Error connecting to user: ${e.message}")
            false
        }
    }

    fun getUserIp(userId: String): String? {
        return udpDiscoveryService.getUserIp(userId)
    }

    fun acceptChatInvitation() {
        _chatInvitation.value = null
        Log.d("ChatApp: UsersViewModel", "Chat invitation accepted")
    }

    fun rejectChatInvitation() {
        _chatInvitation.value = null
        viewModelScope.launch {
            try {
                tcpCommunicationService.closeConnection()
                Log.d("ChatApp: UsersViewModel", "Chat invitation rejected and connection closed")
            } catch (e: Exception) {
                Log.e("ChatApp: UsersViewModel", "Error rejecting chat invitation: ${e.message}")
            }
        }
    }

    fun resetConnectionState() {
        viewModelScope.launch {
            try {
                tcpCommunicationService.closeConnection()
                Log.d("ChatApp: UsersViewModel", "Connection state reset")
            } catch (e: Exception) {
                Log.e("ChatApp: UsersViewModel", "Error resetting connection state: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        udpDiscoveryService.stopDiscoveryServer()
        udpDiscoveryService.clearDiscoveredUsers()
        viewModelScope.launch {
            try {
                tcpCommunicationService.closeConnection()
                Log.d("ChatApp: UsersViewModel", "ViewModel cleared, services stopped")
            } catch (e: Exception) {
                Log.e("ChatApp: UsersViewModel", "Error clearing ViewModel: ${e.message}")
            }
        }
    }
}