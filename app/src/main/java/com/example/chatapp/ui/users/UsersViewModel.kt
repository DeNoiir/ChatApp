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

/**
 * 用户列表界面的 ViewModel
 * 负责处理用户列表、设备发现和聊天邀请等功能
 */
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

    private val _navigateToChatEvent = MutableSharedFlow<String>()
    val navigateToChatEvent = _navigateToChatEvent.asSharedFlow()

    private val _showRejectionDialog = MutableStateFlow(false)
    val showRejectionDialog: StateFlow<Boolean> = _showRejectionDialog

    private lateinit var currentUser: User

    /**
     * 初始化 ViewModel
     *
     * @param userId 当前用户ID
     */
    fun initialize(userId: String) {
        viewModelScope.launch {
            try {
                currentUser = userRepository.getUserById(userId) ?: throw Exception("未找到用户")
                loadUsers()
                startDiscoveryServer()
                startTcpServer()
                collectTcpEvents()
            } catch (e: Exception) {
                Log.e("ChatApp: UsersViewModel", "初始化错误: ${e.message}")
            }
        }
    }

    /**
     * 加载用户列表
     */
    private fun loadUsers() {
        viewModelScope.launch {
            userRepository.getAllUsers().collect { userList ->
                _users.value = userList.filter { it.id != currentUser.id }
            }
        }
    }

    /**
     * 发现设备
     */
    fun discoverDevices() {
        viewModelScope.launch {
            try {
                val discoveredDevices = udpDiscoveryService.discoverDevices(currentUser.id, currentUser.name)
                _discoveredUsers.value = discoveredDevices.map { (id, name, _) ->
                    User(id = id, name = name)
                }
                // 更新数据库中的已发现用户
                discoveredDevices.forEach { (id, name, _) ->
                    userRepository.insertOrUpdateUser(id, name)
                }
            } catch (e: Exception) {
                Log.e("ChatApp: UsersViewModel", "发现设备错误: ${e.message}")
            }
        }
    }

    /**
     * 启动发现服务器
     */
    private fun startDiscoveryServer() {
        udpDiscoveryService.startDiscoveryServer(currentUser.id, currentUser.name)
    }

    /**
     * 启动TCP服务器
     */
    private fun startTcpServer() {
        tcpCommunicationService.startServer { userId, userName ->
            viewModelScope.launch {
                _chatInvitationState.value = ChatInvitationState.Received(userId, userName)
            }
        }
    }

    /**
     * 收集TCP事件
     */
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

    /**
     * 发送聊天邀请
     *
     * @param userId 被邀请用户的ID
     */
    fun sendChatInvitation(userId: String) {
        viewModelScope.launch {
            try {
                val ip = udpDiscoveryService.getUserIp(userId)
                if (ip != null && tcpCommunicationService.connectToUser(ip, currentUser.id, currentUser.name)) {
                    _chatInvitationState.value = ChatInvitationState.Sent(userId)
                } else {
                    _chatInvitationState.value = ChatInvitationState.Error("无法连接到用户")
                }
            } catch (e: Exception) {
                _chatInvitationState.value = ChatInvitationState.Error("发送聊天邀请时出错：${e.message}")
            }
        }
    }

    /**
     * 接受聊天邀请
     */
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

    /**
     * 拒绝聊天邀请
     */
    fun rejectChatInvitation() {
        viewModelScope.launch {
            tcpCommunicationService.sendMessage(MessageType.CHAT_INVITATION_RESPONSE, false)
            tcpCommunicationService.closeConnection()
            _chatInvitationState.value = ChatInvitationState.None
        }
    }

    /**
     * 重置聊天邀请状态
     */
    fun resetChatInvitationState() {
        _chatInvitationState.value = ChatInvitationState.None
    }

    /**
     * 关闭拒绝对话框
     */
    fun dismissRejectionDialog() {
        _showRejectionDialog.value = false
    }

    /**
     * ViewModel 被清除时的操作
     */
    override fun onCleared() {
        super.onCleared()
        udpDiscoveryService.stopDiscoveryServer()
        udpDiscoveryService.clearDiscoveredUsers()
        viewModelScope.launch {
            tcpCommunicationService.closeConnection()
        }
    }
}

/**
 * 聊天邀请状态
 */
sealed class ChatInvitationState {
    /**
     * 无邀请状态
     */
    data object None : ChatInvitationState()

    /**
     * 已发送邀请状态
     *
     * @property userId 被邀请用户的ID
     */
    data class Sent(val userId: String) : ChatInvitationState()

    /**
     * 收到邀请状态
     *
     * @property userId 发送邀请用户的ID
     * @property userName 发送邀请用户的名称
     */
    data class Received(val userId: String, val userName: String) : ChatInvitationState()

    /**
     * 错误状态
     *
     * @property message 错误信息
     */
    data class Error(val message: String) : ChatInvitationState()
}