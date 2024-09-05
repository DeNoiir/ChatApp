package com.example.chatapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.model.User
import com.example.chatapp.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _settingsState = MutableStateFlow<SettingsState>(SettingsState.Initial)
    val settingsState: StateFlow<SettingsState> = _settingsState

    fun loadUser(userId: String) {
        viewModelScope.launch {
            _user.value = userRepository.getUserById(userId)
        }
    }

    fun updatePassword(userId: String, oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            val currentUser = userRepository.getUserById(userId)
            if (currentUser != null && currentUser.password == oldPassword) {
                val updatedUser = currentUser.copy(password = newPassword)
                userRepository.updateUser(updatedUser)
                _settingsState.value = SettingsState.PasswordUpdated
                _user.value = updatedUser
            } else {
                _settingsState.value = SettingsState.Error("Invalid old password")
            }
        }
    }
}

sealed class SettingsState {
    object Initial : SettingsState()
    object PasswordUpdated : SettingsState()
    data class Error(val message: String) : SettingsState()
}