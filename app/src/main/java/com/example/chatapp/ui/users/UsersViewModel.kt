package com.example.chatapp.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.model.User
import com.example.chatapp.data.repository.UserRepository
import com.example.chatapp.network.ConnectionState
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

    val connectionState = tcpCommunicationService.connectionState

    private lateinit var currentUser: User
    private val userIpMap = mutableMapOf<String, String>()

    fun initialize(userId: String) {
        viewModelScope.launch {
            currentUser = userRepository.getUserById(userId) ?: return@launch
            loadUsers()
            startDiscoveryServer()
            startTcpServer()
        }
    }

    private fun loadUsers() {
        viewModelScope.launch {
            userRepository.getAllUsers().collect { userList ->
                _users.value = userList.filter { it.id != currentUser.id }
            }
        }
    }

    fun discoverDevices() {
        viewModelScope.launch {
            val discoveredDevices = udpDiscoveryService.discoverDevices(currentUser.id, currentUser.name)
            val newUsers = discoveredDevices.map { (id, name, ip) ->
                userIpMap[id] = ip
                userRepository.insertOrUpdateUser(id, name)
                User(id = id, name = name)
            }
            _discoveredUsers.value = newUsers
        }
    }

    private fun startDiscoveryServer() {
        udpDiscoveryService.startDiscoveryServer(currentUser.id, currentUser.name)
    }

    private fun startTcpServer() {
        tcpCommunicationService.startServer { userId ->
            viewModelScope.launch {
                val user = userRepository.getUserById(userId)
                if (user != null) {
                    _chatInvitation.value = Pair(userId, user.name)
                }
            }
        }
    }

    suspend fun connectToUser(userId: String): Boolean {
        val ipAddress = userIpMap[userId] ?: return false
        return tcpCommunicationService.connectToUser(ipAddress, currentUser.id)
    }

    fun acceptChatInvitation() {
        _chatInvitation.value = null
    }

    fun rejectChatInvitation() {
        _chatInvitation.value = null
        viewModelScope.launch {
            tcpCommunicationService.closeConnection()
        }
    }

    fun resetConnectionState() {
        viewModelScope.launch {
            tcpCommunicationService.closeConnection()
        }
    }

    override fun onCleared() {
        super.onCleared()
        udpDiscoveryService.stopDiscoveryServer()
        viewModelScope.launch {
            tcpCommunicationService.closeConnection()
        }
    }
}