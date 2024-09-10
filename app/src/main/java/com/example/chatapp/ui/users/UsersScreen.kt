package com.example.chatapp.ui.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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