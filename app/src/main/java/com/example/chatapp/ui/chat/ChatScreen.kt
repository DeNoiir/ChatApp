package com.example.chatapp.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chatapp.data.model.Message
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUserId: String,
    otherUserId: String,
    isReadOnly: Boolean,
    viewModel: ChatViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val otherUserName by viewModel.otherUserName.collectAsState()
    val chatState by viewModel.chatState.collectAsState()
    val fileTransferState by viewModel.fileTransferState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showExitDialog by remember { mutableStateOf(false) }
    var showOptionsDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.prepareFileTransfer(it) }
    }

    val fileSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { destinationUri ->
            val currentState = fileTransferState
            if (currentState is FileTransferState.Completed && currentState.tempFile != null) {
                viewModel.saveFile(currentState.tempFile, destinationUri)
            }
        }
    }

    LaunchedEffect(currentUserId, otherUserId) {
        viewModel.initialize(currentUserId, otherUserId)
    }

    LaunchedEffect(Unit) {
        viewModel.fileSaveEvent.collect { event ->
            fileSaveLauncher.launch(event.fileName)
        }
    }

    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(otherUserName) },
                navigationIcon = {
                    IconButton(onClick = { showExitDialog = true }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showOptionsDialog = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
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
                state = listState,
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
            if (!isReadOnly) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        enabled = chatState == ChatState.Active
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                                coroutineScope.launch {
                                    listState.animateScrollToItem(messages.size)
                                }
                            }
                        },
                        enabled = chatState == ChatState.Active
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                    IconButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        enabled = chatState == ChatState.Active
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Send File")
                    }
                }
            }
        }
    }

    when (val state = fileTransferState) {
        is FileTransferState.AwaitingConfirmation -> {
            AlertDialog(
                onDismissRequest = { viewModel.cancelFileTransfer() },
                title = { Text("Confirm File Transfer") },
                text = { Text("Do you want to send the file: ${state.fileName}?") },
                confirmButton = {
                    Button(onClick = { viewModel.confirmFileTransfer() }) {
                        Text("Send")
                    }
                },
                dismissButton = {
                    Button(onClick = { viewModel.cancelFileTransfer() }) {
                        Text("Cancel")
                    }
                }
            )
        }
        is FileTransferState.WaitingForAcceptance -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Waiting for Acceptance") },
                text = { Text("Waiting for the other user to accept the file transfer...") },
                confirmButton = { }
            )
        }
        is FileTransferState.Sending -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Sending File") },
                text = {
                    Column {
                        Text("Sending: ${state.fileName}")
                        LinearProgressIndicator(
                            progress = state.progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = { }
            )
        }
        is FileTransferState.Receiving -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Receiving File") },
                text = {
                    Column {
                        Text("Receiving: ${state.fileName}")
                        LinearProgressIndicator(
                            progress = state.progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = { }
            )
        }
        is FileTransferState.ReceivingRequest -> {
            AlertDialog(
                onDismissRequest = { viewModel.rejectFileTransfer() },
                title = { Text("File Transfer Request") },
                text = { Text("Do you want to receive the file: ${state.fileName}?") },
                confirmButton = {
                    Button(onClick = { viewModel.acceptFileTransfer() }) {
                        Text("Accept")
                    }
                },
                dismissButton = {
                    Button(onClick = { viewModel.rejectFileTransfer() }) {
                        Text("Reject")
                    }
                }
            )
        }
        is FileTransferState.Completed -> {
            LaunchedEffect(Unit) {
                viewModel.resetFileTransferState()
            }
        }
        is FileTransferState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetFileTransferState() },
                title = { Text("File Transfer Error") },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = { viewModel.resetFileTransferState() }) {
                        Text("OK")
                    }
                }
            )
        }
        is FileTransferState.Idle -> {
            // No dialog shown for Idle state
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit Chat") },
            text = { Text("Are you sure you want to exit the chat?") },
            confirmButton = {
                Button(onClick = {
                    showExitDialog = false
                    viewModel.endChat()
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                Button(onClick = { showExitDialog = false }) {
                    Text("No")
                }
            }
        )
    }

    if (chatState == ChatState.Ended) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Chat Ended") },
            text = { Text("The chat has ended.") },
            confirmButton = {
                Button(onClick = { onBackClick() }) {
                    Text("OK")
                }
            }
        )
    }

    if (showOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text("Chat Options") },
            text = {
                Column {
                    Text("Filter Messages:", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    FilterType.values().forEach { filterType ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = viewModel.filterType.value == filterType,
                                onClick = {
                                    viewModel.setFilterType(filterType)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(filterType.name)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.clearAllMessages()
                            showOptionsDialog = false
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Clear All Messages")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showOptionsDialog = false }) {
                    Text("Close")
                }
            }
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
            when (message.type) {
                0 -> Text(
                    text = message.content,
                    modifier = Modifier.padding(8.dp)
                )
                1 -> {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = "File")
                        Text(text = "File: ${message.content}")
                    }
                }
            }
        }
    }
}