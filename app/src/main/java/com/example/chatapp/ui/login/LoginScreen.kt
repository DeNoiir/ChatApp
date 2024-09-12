package com.example.chatapp.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 登录界面组件
 *
 * @param viewModel 登录界面的ViewModel
 * @param onLoginSuccess 登录成功的回调函数
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: (String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val loginState by viewModel.loginState.collectAsState()
    val usernameError by viewModel.usernameError.collectAsState()
    val passwordError by viewModel.passwordError.collectAsState()

    LaunchedEffect(loginState) {
        when (loginState) {
            is LoginState.LoginSuccess -> {
                onLoginSuccess((loginState as LoginState.LoginSuccess).user.id)
            }
            is LoginState.RegisterSuccess -> {
                // 不立即导航，等待用户关闭对话框
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Login,
            contentDescription = "登录",
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                viewModel.clearErrors()
            },
            label = { Text("用户名") },
            isError = usernameError != null,
            supportingText = {
                if (usernameError != null) {
                    Text(usernameError!!, color = MaterialTheme.colorScheme.error)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                viewModel.clearErrors()
            },
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            isError = passwordError != null,
            supportingText = {
                if (passwordError != null) {
                    Text(passwordError!!, color = MaterialTheme.colorScheme.error)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.loginOrRegister(username, password) },
            modifier = Modifier.widthIn(min = 200.dp, max = 300.dp)
        ) {
            Text("登录 / 注册")
        }

        if (loginState is LoginState.Error) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = (loginState as LoginState.Error).message,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    if (loginState is LoginState.RegisterSuccess) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("注册成功") },
            text = { Text("您的账户已成功创建。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearLoginState()
                        onLoginSuccess((loginState as LoginState.RegisterSuccess).user.id)
                    }
                ) {
                    Text("确定")
                }
            }
        )
    }
}