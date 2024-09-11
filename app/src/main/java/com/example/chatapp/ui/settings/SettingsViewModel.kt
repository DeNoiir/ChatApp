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

    fun loadUser(userId: String) {
        viewModelScope.launch {
            _user.value = userRepository.getUserById(userId)
        }
    }

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
                _settingsState.value = SettingsState.Error("Invalid old password")
            }
        }
    }

    private fun validateInput(oldPassword: String, newPassword: String): Boolean {
        var isValid = true

        if (oldPassword.isBlank()) {
            _oldPasswordError.value = "Old password cannot be empty"
            isValid = false
        } else {
            _oldPasswordError.value = null
        }

        if (newPassword.length < 6) {
            _newPasswordError.value = "New password must be at least 6 characters long"
            isValid = false
        } else {
            _newPasswordError.value = null
        }

        return isValid
    }

    fun clearErrors() {
        _oldPasswordError.value = null
        _newPasswordError.value = null
    }

    fun dismissPasswordUpdatedDialog() {
        _showPasswordUpdatedDialog.value = false
    }

    fun showLogoutConfirmDialog() {
        _showLogoutConfirmDialog.value = true
    }

    fun dismissLogoutConfirmDialog() {
        _showLogoutConfirmDialog.value = false
    }

    fun logout() {
        viewModelScope.launch {
            // Stop UDP discovery service
            udpDiscoveryService.stopDiscoveryServer()
            udpDiscoveryService.clearDiscoveredUsers()

            // Close TCP connections
            tcpCommunicationService.closeConnection()

            // Clear any other app state if necessary
            // For example, clear any cached user data
            _user.value = null

            // You might want to clear any other app-wide state here

            _settingsState.value = SettingsState.LoggedOut
        }
    }
}

sealed class SettingsState {
    data object Initial : SettingsState()
    data object PasswordUpdated : SettingsState()
    data class Error(val message: String) : SettingsState()
    data object LoggedOut : SettingsState()
}