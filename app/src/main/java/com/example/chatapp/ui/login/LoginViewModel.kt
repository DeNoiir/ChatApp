package com.example.chatapp.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.ChatApplication
import com.example.chatapp.data.model.User
import com.example.chatapp.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    application: Application
) : AndroidViewModel(application) {

    private val chatApplication: ChatApplication
        get() = getApplication() as ChatApplication

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Initial)
    val loginState: StateFlow<LoginState> = _loginState

    fun login(username: String, password: String) {
        viewModelScope.launch {
            val user = userRepository.getUserByName(username)
            if (user != null && user.password == password) {
                chatApplication.setUserInfo(user.id, user.name)
                _loginState.value = LoginState.Success(user)
            } else {
                _loginState.value = LoginState.Error("Invalid username or password")
            }
        }
    }

    fun register(username: String, password: String) {
        viewModelScope.launch {
            val existingUser = userRepository.getUserByName(username)
            if (existingUser != null) {
                _loginState.value = LoginState.Error("Username already exists")
            } else {
                val newUser = User(id = UUID.randomUUID().toString(), name = username, password = password)
                userRepository.insertUser(newUser)
                chatApplication.setUserInfo(newUser.id, newUser.name)
                _loginState.value = LoginState.Success(newUser)
            }
        }
    }
}

sealed class LoginState {
    object Initial : LoginState()
    data class Success(val user: User) : LoginState()
    data class Error(val message: String) : LoginState()
}