package com.example.chatapp.ui.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chatapp.data.model.User

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

    LaunchedEffect(userId) {
        viewModel.initialize(userId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Text(
                    "Discovered Users",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(discoveredUsers) { user ->
                UserItem(user) { onUserSelected(user.id) }
            }
            item {
                Text(
                    "Known Users",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(users) { user ->
                UserItem(user) { onUserSelected(user.id) }
            }
        }
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