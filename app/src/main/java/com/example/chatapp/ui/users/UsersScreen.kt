package com.example.chatapp.ui.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chatapp.data.model.User
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    userId: String,
    viewModel: UsersViewModel = hiltViewModel(),
    onUserSelected: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val users by viewModel.users.collectAsState()
    val discoveredUsers by viewModel.discoveredUsers.collectAsState()
    val chatInvitation by viewModel.chatInvitation.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        viewModel.initialize(userId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Users") },
                actions = {
                    IconButton(onClick = { viewModel.discoverDevices() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Discover devices")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                Text(
                    "Discovered Users",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(discoveredUsers) { user ->
                UserItem(user) {
                    scope.launch {
                        if (viewModel.connectToUser(user.id)) {
                            onUserSelected(user.id)
                        }
                    }
                }
            }
            item {
                Text(
                    "Known Users",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(users) { user ->
                UserItem(user) {
                    scope.launch {
                        if (viewModel.connectToUser(user.id)) {
                            onUserSelected(user.id)
                        }
                    }
                }
            }
        }
    }

    chatInvitation?.let { (userId, userName) ->
        AlertDialog(
            onDismissRequest = { viewModel.rejectChatInvitation() },
            title = { Text("Chat Invitation") },
            text = { Text("$userName wants to chat with you.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.acceptChatInvitation()
                    onUserSelected(userId)
                }) {
                    Text("Accept")
                }
            },
            dismissButton = {
                Button(onClick = { viewModel.rejectChatInvitation() }) {
                    Text("Reject")
                }
            }
        )
    }
}

@Composable
fun UserItem(user: User, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = user.name,
            modifier = Modifier.padding(16.dp)
        )
    }
}