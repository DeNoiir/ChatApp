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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                                        <h2>User Information</h2>
                                        <p><strong>Username:</strong> ${currentUser.name}</p>
                                        <p><strong>User ID:</strong> ${currentUser.id}</p>
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
                    label = { Text("Old Password") },
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
                    label = { Text("New Password") },
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
                    Text("Update Password")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.showLogoutConfirmDialog() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Logout")
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

    if (showPasswordUpdatedDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPasswordUpdatedDialog() },
            title = { Text("Password Updated") },
            text = { Text("Your password has been successfully updated.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissPasswordUpdatedDialog()
                    oldPassword = ""
                    newPassword = ""
                }) {
                    Text("OK")
                }
            }
        )
    }

    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLogoutConfirmDialog() },
            title = { Text("Confirm Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissLogoutConfirmDialog()
                    viewModel.logout()
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                Button(onClick = { viewModel.dismissLogoutConfirmDialog() }) {
                    Text("No")
                }
            }
        )
    }
}