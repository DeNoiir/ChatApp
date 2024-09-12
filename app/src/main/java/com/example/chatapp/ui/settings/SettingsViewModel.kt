package com.example.chatapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.model.User
import com.example.chatapp.data.repository.UserRepository
import com.example.chatapp.network.UdpDiscoveryService
import com.example.chatapp.network.TcpCommunicationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置界面的 ViewModel
 * 负责处理用户设置相关的业务逻辑
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val udpDiscoveryService: UdpDiscoveryService,
    private val tcpCommunicationService: TcpCommunicationService
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _settingsState = MutableStateFlow<SettingsState>(SettingsState.Initial)
    val settingsState: StateFlow<SettingsState> = _settingsState

    private val _oldPasswordError = MutableStateFlow<String?>(null)
    val oldPasswordError: StateFlow<String?> = _oldPasswordError

    private val _newPasswordError = MutableStateFlow<String?>(null)
    val newPasswordError: StateFlow<String?> = _newPasswordError

    private val _showPasswordUpdatedDialog = MutableStateFlow(false)
    val showPasswordUpdatedDialog: StateFlow<Boolean> = _showPasswordUpdatedDialog

    private val _showLogoutConfirmDialog = MutableStateFlow(false)
    val showLogoutConfirmDialog: StateFlow<Boolean> = _showLogoutConfirmDialog

    /**
     * 加载用户信息
     *
     * @param userId 用户ID
     */
    fun loadUser(userId: String) {
        viewModelScope.launch {
            _user.value = userRepository.getUserById(userId)
        }
    }

    /**
     * 更新密码
     *
     * @param userId 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    fun updatePassword(userId: String, oldPassword: String, newPassword: String) {
        if (!validateInput(oldPassword, newPassword)) {
            return
        }

        viewModelScope.launch {
            val currentUser = userRepository.getUserById(userId)
            if (currentUser != null && currentUser.password == oldPassword) {
                val updatedUser = currentUser.copy(password = newPassword)
                userRepository.updateUser(updatedUser)
                _settingsState.value = SettingsState.PasswordUpdated
                _user.value = updatedUser
                _showPasswordUpdatedDialog.value = true
            } else {
                _settingsState.value = SettingsState.Error("旧密码不正确")
            }
        }
    }

    /**
     * 验证密码输入
     *
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 输入是否有效
     */
    private fun validateInput(oldPassword: String, newPassword: String): Boolean {
        var isValid = true

        if (oldPassword.isBlank()) {
            _oldPasswordError.value = "旧密码不能为空"
            isValid = false
        } else {
            _oldPasswordError.value = null
        }

        if (newPassword.length < 6) {
            _newPasswordError.value = "新密码至少需要6个字符"
            isValid = false
        } else {
            _newPasswordError.value = null
        }

        return isValid
    }

    /**
     * 清除错误信息
     */
    fun clearErrors() {
        _oldPasswordError.value = null
        _newPasswordError.value = null
    }

    /**
     * 关闭密码更新成功对话框
     */
    fun dismissPasswordUpdatedDialog() {
        _showPasswordUpdatedDialog.value = false
    }

    /**
     * 显示登出确认对话框
     */
    fun showLogoutConfirmDialog() {
        _showLogoutConfirmDialog.value = true
    }

    /**
     * 关闭登出确认对话框
     */
    fun dismissLogoutConfirmDialog() {
        _showLogoutConfirmDialog.value = false
    }

    /**
     * 执行登出操作
     */
    fun logout() {
        viewModelScope.launch {
            // 停止UDP发现服务
            udpDiscoveryService.stopDiscoveryServer()
            udpDiscoveryService.clearDiscoveredUsers()

            // 关闭TCP连接
            tcpCommunicationService.closeConnection()

            // 清除用户数据
            _user.value = null

            // 可以在这里清除其他应用范围的状态

            _settingsState.value = SettingsState.LoggedOut
        }
    }
}

/**
 * 设置状态
 */
sealed class SettingsState {
    /**
     * 初始状态
     */
    data object Initial : SettingsState()

    /**
     * 密码已更新状态
     */
    data object PasswordUpdated : SettingsState()

    /**
     * 错误状态
     *
     * @property message 错误信息
     */
    data class Error(val message: String) : SettingsState()

    /**
     * 已登出状态
     */
    data object LoggedOut : SettingsState()
}