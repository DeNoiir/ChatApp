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

/**
 * 登录界面的 ViewModel
 * 负责处理登录和注册相关的业务逻辑
 */
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

    /**
     * 执行登录或注册操作
     *
     * @param username 用户名
     * @param password 密码
     */
    fun loginOrRegister(username: String, password: String) {
        if (!validateInput(username, password)) return

        viewModelScope.launch {
            val existingUser = userRepository.getUserByName(username)
            if (existingUser != null) {
                // 登录
                if (existingUser.password == password) {
                    _loginState.value = LoginState.LoginSuccess(existingUser)
                } else {
                    _loginState.value = LoginState.Error("密码不正确")
                }
            } else {
                // 注册
                val newUser = User(id = UUID.randomUUID().toString(), name = username, password = password)
                userRepository.insertUser(newUser)
                _loginState.value = LoginState.RegisterSuccess(newUser)
            }
        }
    }

    /**
     * 验证输入的用户名和密码
     *
     * @param username 用户名
     * @param password 密码
     * @return 输入是否有效
     */
    private fun validateInput(username: String, password: String): Boolean {
        var isValid = true

        if (username.length < 3) {
            _usernameError.value = "用户名至少需要3个字符"
            isValid = false
        } else {
            _usernameError.value = null
        }

        if (password.length < 6) {
            _passwordError.value = "密码至少需要6个字符"
            isValid = false
        } else {
            _passwordError.value = null
        }

        return isValid
    }

    /**
     * 清除错误信息
     */
    fun clearErrors() {
        _usernameError.value = null
        _passwordError.value = null
    }

    /**
     * 清除登录状态
     */
    fun clearLoginState() {
        _loginState.value = LoginState.Initial
    }
}

/**
 * 登录状态
 */
sealed class LoginState {
    /**
     * 初始状态
     */
    data object Initial : LoginState()

    /**
     * 登录成功
     *
     * @property user 登录成功的用户
     */
    data class LoginSuccess(val user: User) : LoginState()

    /**
     * 注册成功
     *
     * @property user 注册成功的用户
     */
    data class RegisterSuccess(val user: User) : LoginState()

    /**
     * 错误状态
     *
     * @property message 错误信息
     */
    data class Error(val message: String) : LoginState()
}