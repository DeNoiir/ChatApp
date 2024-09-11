package com.example.chatapp.ui.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chatapp.data.model.User
import com.example.chatapp.network.ChatEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    userId: String,
    viewModel: UsersViewModel = hiltViewModel(),
    onNavigateToChat: (String, Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    val users by viewModel.users.collectAsState()
    val discoveredUsers by viewModel.discoveredUsers.collectAsState()
    val chatInvitationState by viewModel.chatInvitationState.collectAsState()
    val showRejectionDialog by viewModel.showRejectionDialog.collectAsState()

    LaunchedEffect(userId) {
        viewModel.initialize(userId)
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToChatEvent.collect { otherUserId ->
            onNavigateToChat(otherUserId, false)  // false indicates it's not read-only
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat App") },
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
                UserItem(
                    user = user,
                    icon = Icons.Default.PersonAdd,
                    iconDescription = "New User",
                    onClick = { viewModel.sendChatInvitation(user.id) }
                )
            }
            item {
                Text(
                    "Known Users",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(users.filter { knownUser ->
                discoveredUsers.none { it.id == knownUser.id }
            }) { user ->
                UserItem(
                    user = user,
                    icon = Icons.Default.History,
                    iconDescription = "Chat History",
                    onClick = { onNavigateToChat(user.id, true) }  // true indicates it's read-only
                )
            }
        }
    }

    when (val state = chatInvitationState) {
        is ChatInvitationState.Sent -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetChatInvitationState() },
                title = { Text("Waiting for response") },
                text = { Text("Waiting for the other user to accept your invitation...") },
                confirmButton = {}
            )
        }
        is ChatInvitationState.Received -> {
            AlertDialog(
                onDismissRequest = { viewModel.rejectChatInvitation() },
                title = { Text("Chat Invitation") },
                text = { Text("${state.userName} wants to chat with you.") },
                confirmButton = {
                    Button(onClick = { viewModel.acceptChatInvitation() }) {
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
        is ChatInvitationState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetChatInvitationState() },
                title = { Text("Error") },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = { viewModel.resetChatInvitationState() }) {
                        Text("OK")
                    }
                }
            )
        }
        else -> {} // Handle other states if needed
    }

    if (showRejectionDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRejectionDialog() },
            title = { Text("Invitation Rejected") },
            text = { Text("The user has rejected your chat invitation.") },
            confirmButton = {
                Button(onClick = { viewModel.dismissRejectionDialog() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun UserItem(
    user: User,
    icon: ImageVector,
    iconDescription: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}