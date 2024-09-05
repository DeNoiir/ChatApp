package com.example.chatapp.ui.settings

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SettingsScreen(
    userId: String,
    viewModel: SettingsViewModel = hiltViewModel(),
    onLogout: () -> Unit
) {
    val user by viewModel.user.collectAsState()
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        viewModel.loadUser(userId)
    }

    LaunchedEffect(viewModel) {
        viewModel.settingsState.collectLatest { state ->
            when (state) {
                is SettingsState.PasswordUpdated -> {
                    isLoading = false
                    errorMessage = null
                    oldPassword = ""
                    newPassword = ""
                }
                is SettingsState.Error -> {
                    isLoading = false
                    errorMessage = state.message
                }
                SettingsState.Initial -> {
                    isLoading = false
                    errorMessage = null
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        user?.let { currentUser ->
            // HTML mixed programming for user info display
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
            TextField(
                value = oldPassword,
                onValueChange = { oldPassword = it },
                label = { Text("Old Password") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("New Password") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    viewModel.updatePassword(currentUser.id, oldPassword, newPassword)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Update Password")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Logout")
        }
        if (isLoading) {
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator()
        }
        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}