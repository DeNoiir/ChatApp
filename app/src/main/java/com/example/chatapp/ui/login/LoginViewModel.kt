package com.example.chatapp.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val userRepository: UserRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Initial)
    val loginState: StateFlow<LoginState> = _loginState

    private val _usernameError = MutableStateFlow<String?>(null)
    val usernameError: StateFlow<String?> = _usernameError

    private val _passwordError = MutableStateFlow<String?>(null)
    val passwordError: StateFlow<String?> = _passwordError

    fun loginOrRegister(username: String, password: String) {
        if (!validateInput(username, password)) return

        viewModelScope.launch {
            val existingUser = userRepository.getUserByName(username)
            if (existingUser != null) {
                // Login
                if (existingUser.password == password) {
                    _loginState.value = LoginState.LoginSuccess(existingUser)
                } else {
                    _loginState.value = LoginState.Error("Incorrect password")
                }
            } else {
                // Register
                val newUser = User(id = UUID.randomUUID().toString(), name = username, password = password)
                userRepository.insertUser(newUser)
                _loginState.value = LoginState.RegisterSuccess(newUser)
            }
        }
    }

    private fun validateInput(username: String, password: String): Boolean {
        var isValid = true

        if (username.length < 3) {
            _usernameError.value = "Username must be at least 3 characters long"
            isValid = false
        } else {
            _usernameError.value = null
        }

        if (password.length < 6) {
            _passwordError.value = "Password must be at least 6 characters long"
            isValid = false
        } else {
            _passwordError.value = null
        }

        return isValid
    }

    fun clearErrors() {
        _usernameError.value = null
        _passwordError.value = null
    }

    fun clearLoginState() {
        _loginState.value = LoginState.Initial
    }
}

sealed class LoginState {
    data object Initial : LoginState()
    data class LoginSuccess(val user: User) : LoginState()
    data class RegisterSuccess(val user: User) : LoginState()
    data class Error(val message: String) : LoginState()
}