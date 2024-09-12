package com.example.chatapp.ui.settings

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 设置界面的主要组件
 *
 * @param userId 当前用户ID
 * @param viewModel 设置界面的ViewModel
 * @param onLogout 登出回调函数
 * @param onBackClick 返回按钮点击回调
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userId: String,
    viewModel: SettingsViewModel = hiltViewModel(),
    onLogout: () -> Unit,
    onBackClick: () -> Unit
) {
    val user by viewModel.user.collectAsState()
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    val oldPasswordError by viewModel.oldPasswordError.collectAsState()
    val newPasswordError by viewModel.newPasswordError.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()
    val showPasswordUpdatedDialog by viewModel.showPasswordUpdatedDialog.collectAsState()
    val showLogoutConfirmDialog by viewModel.showLogoutConfirmDialog.collectAsState()

    LaunchedEffect(userId) {
        viewModel.loadUser(userId)
    }

    LaunchedEffect(settingsState) {
        if (settingsState is SettingsState.LoggedOut) {
            onLogout()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            user?.let { currentUser ->
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            loadData(
                                """
                                <html>
                                    <body>
                                        <h2>用户信息</h2>
                                        <p><strong>用户名:</strong> ${currentUser.name}</p>
                                        <p><strong>用户ID:</strong> ${currentUser.id}</p>
                                    </body>
                                </html>
                                """.trimIndent(),
                                "text/html",
                                "UTF-8"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = {
                        oldPassword = it
                        viewModel.clearErrors()
                    },
                    label = { Text("旧密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = oldPasswordError != null,
                    supportingText = {
                        if (oldPasswordError != null) {
                            Text(oldPasswordError!!, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        viewModel.clearErrors()
                    },
                    label = { Text("新密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = newPasswordError != null,
                    supportingText = {
                        if (newPasswordError != null) {
                            Text(newPasswordError!!, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.updatePassword(currentUser.id, oldPassword, newPassword)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("更新密码")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.showLogoutConfirmDialog() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("登出")
            }
            if (settingsState is SettingsState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    (settingsState as SettingsState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    /**
     * 密码更新成功对话框
     */
    if (showPasswordUpdatedDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPasswordUpdatedDialog() },
            title = { Text("密码已更新") },
            text = { Text("您的密码已成功更新。") },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissPasswordUpdatedDialog()
                    oldPassword = ""
                    newPassword = ""
                }) {
                    Text("确定")
                }
            }
        )
    }

    /**
     * 登出确认对话框
     */
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLogoutConfirmDialog() },
            title = { Text("确认登出") },
            text = { Text("您确定要登出吗？") },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissLogoutConfirmDialog()
                    viewModel.logout()
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                Button(onClick = { viewModel.dismissLogoutConfirmDialog() }) {
                    Text("取消")
                }
            }
        )
    }
}