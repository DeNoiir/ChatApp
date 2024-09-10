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
import kotlinx.coroutines.flow.collectLatest

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { onBackClick() }) {
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
                        isLoading = true
                        errorMessage = null
                        viewModel.updatePassword(currentUser.id, oldPassword, newPassword) {
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
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
}