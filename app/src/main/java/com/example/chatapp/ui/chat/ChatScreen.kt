package com.example.chatapp.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chatapp.data.model.Message
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUserId: String,
    otherUserId: String,
    viewModel: ChatViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val otherUserName by viewModel.otherUserName.collectAsState()
    val isNavigatingBack by viewModel.isNavigatingBack.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showFilterDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentUserId, otherUserId) {
        viewModel.loadMessages(currentUserId, otherUserId)
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is NavigationEvent.NavigateBack -> {
                    onBackClick()
                }
            }
        }
    }

    BackHandler {
        if (!isNavigatingBack) {
            viewModel.closeChat()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(otherUserName) },
                navigationIcon = {
                    IconButton(onClick = {
                            viewModel.closeChat()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                    IconButton(onClick = { viewModel.clearAllMessages(currentUserId, otherUserId) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear messages")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(messages) { message ->
                    MessageItem(
                        message = message,
                        isCurrentUser = message.sender == currentUserId
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(currentUserId, otherUserId, inputText)
                            inputText = ""
                        }
                    }
                ) {
                    Text("Send")
                }
            }
        }
    }

    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Filter Messages") },
            text = {
                Column {
                    FilterType.values().forEach { filterType ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setFilterType(filterType)
                                    showFilterDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = viewModel.filterType.value == filterType,
                                onClick = {
                                    viewModel.setFilterType(filterType)
                                    showFilterDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(filterType.name)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun MessageItem(message: Message, isCurrentUser: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}