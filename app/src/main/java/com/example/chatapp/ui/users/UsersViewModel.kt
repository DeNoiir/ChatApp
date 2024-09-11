package com.example.chatapp.ui.users

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.model.User
import com.example.chatapp.data.repository.UserRepository
import com.example.chatapp.network.MessageType
import com.example.chatapp.network.TcpCommunicationService
import com.example.chatapp.network.UdpDiscoveryService
import com.example.chatapp.network.ChatEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _chatInvitationState = MutableStateFlow<ChatInvitationState>(ChatInvitationState.None)
    val chatInvitationState: StateFlow<ChatInvitationState> = _chatInvitationState

    private val _chatEvent = MutableSharedFlow<ChatEvent>()
    val chatEvent = _chatEvent.asSharedFlow()

    private val _navigateToChatEvent = MutableSharedFlow<String>()
    val navigateToChatEvent = _navigateToChatEvent.asSharedFlow()

    private val _showRejectionDialog = MutableStateFlow(false)
    val showRejectionDialog: StateFlow<Boolean> = _showRejectionDialog

    private lateinit var currentUser: User

    fun initialize(userId: String) {
        viewModelScope.launch {
            try {
                currentUser = userRepository.getUserById(userId) ?: throw Exception("User not found")
                loadUsers()
                startDiscoveryServer()
                startTcpServer()
                collectTcpEvents()
            } catch (e: Exception) {
                Log.e("ChatApp: UsersViewModel", "Error initializing: ${e.message}")
            }
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
    }

    private fun startTcpServer() {
        tcpCommunicationService.startServer { userId, userName ->
            viewModelScope.launch {
                _chatInvitationState.value = ChatInvitationState.Received(userId, userName)
            }
        }
    }

    private fun collectTcpEvents() {
        viewModelScope.launch {
            tcpCommunicationService.chatEvent.collect { event ->
                when (event) {
                    is ChatEvent.ChatInvitationResponse -> {
                        if (event.accepted) {
                            val otherUserId = (_chatInvitationState.value as? ChatInvitationState.Sent)?.userId
                            if (otherUserId != null) {
                                _navigateToChatEvent.emit(otherUserId)
                            }
                        } else {
                            _showRejectionDialog.value = true
                        }
                        _chatInvitationState.value = ChatInvitationState.None
                    }
                    else -> _chatEvent.emit(event)
                }
            }
        }
    }

    fun sendChatInvitation(userId: String) {
        viewModelScope.launch {
            try {
                val ip = udpDiscoveryService.getUserIp(userId)
                if (ip != null && tcpCommunicationService.connectToUser(ip, currentUser.id, currentUser.name)) {
                    _chatInvitationState.value = ChatInvitationState.Sent(userId)
                } else {
                    _chatInvitationState.value = ChatInvitationState.Error("Failed to connect to user")
                }
            } catch (e: Exception) {
                _chatInvitationState.value = ChatInvitationState.Error("Error sending chat invitation: ${e.message}")
            }
        }
    }

    fun viewChatHistory(userId: String) {
        viewModelScope.launch {
            _navigateToChatEvent.emit(userId)
        }
    }

    fun acceptChatInvitation() {
        viewModelScope.launch {
            val state = _chatInvitationState.value
            if (state is ChatInvitationState.Received) {
                tcpCommunicationService.sendMessage(MessageType.CHAT_INVITATION_RESPONSE, true)
                _chatInvitationState.value = ChatInvitationState.None
                _navigateToChatEvent.emit(state.userId)
            }
        }
    }

    fun rejectChatInvitation() {
        viewModelScope.launch {
            tcpCommunicationService.sendMessage(MessageType.CHAT_INVITATION_RESPONSE, false)
            tcpCommunicationService.closeConnection()
            _chatInvitationState.value = ChatInvitationState.None
        }
    }

    fun resetChatInvitationState() {
        _chatInvitationState.value = ChatInvitationState.None
    }

    fun dismissRejectionDialog() {
        _showRejectionDialog.value = false
    }

    override fun onCleared() {
        super.onCleared()
        udpDiscoveryService.stopDiscoveryServer()
        udpDiscoveryService.clearDiscoveredUsers()
        viewModelScope.launch {
            tcpCommunicationService.closeConnection()
        }
    }
}

sealed class ChatInvitationState {
    object None : ChatInvitationState()
    data class Sent(val userId: String) : ChatInvitationState()
    data class Received(val userId: String, val userName: String) : ChatInvitationState()
    data class Error(val message: String) : ChatInvitationState()
}