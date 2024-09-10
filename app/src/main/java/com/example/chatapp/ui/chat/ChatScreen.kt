package com.example.chatapp.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chatapp.data.model.Message
import com.example.chatapp.network.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUserId: String,
    otherUserId: String,
    otherUserIp: String,
    viewModel: ChatViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val otherUserName by viewModel.otherUserName.collectAsState()
    val isNavigatingBack by viewModel.isNavigatingBack.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val fileTransferProgress by viewModel.fileTransferProgress.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showFileTransferProgress by remember { mutableStateOf(false) }
    var fileTransferStatus by remember { mutableStateOf<String?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendFile(it) }
    }

    LaunchedEffect(currentUserId, otherUserId, otherUserIp) {
        viewModel.initialize(currentUserId, otherUserId, otherUserIp)
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

    LaunchedEffect(Unit) {
        viewModel.fileEvent.collect { event ->
            when (event) {
                is FileEvent.SendingFile -> {
                    showFileTransferProgress = true
                    fileTransferStatus = "Preparing to send file: ${event.fileName}"
                }
                is FileEvent.FileSent -> {
                    showFileTransferProgress = false
                    fileTransferStatus = "File sent: ${event.fileName}"
                }
                is FileEvent.ReceivingFile -> {
                    showFileTransferProgress = true
                    fileTransferStatus = "Receiving file: ${event.fileName}"
                }
                is FileEvent.FileReceived -> {
                    showFileTransferProgress = false
                    fileTransferStatus = "File received and saved: ${event.fileName}"
                }
                is FileEvent.FileError -> {
                    showFileTransferProgress = false
                    errorMessage = event.error
                    showErrorDialog = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(otherUserName) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeChat() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                    IconButton(onClick = { viewModel.clearAllMessages() }) {
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
                IconButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    enabled = connectionState == ConnectionState.Connected
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Send File")
                }
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    enabled = connectionState == ConnectionState.Connected
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = connectionState == ConnectionState.Connected
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

    if (showFileTransferProgress) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("File Transfer") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = fileTransferProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Transfer Progress: ${(fileTransferProgress * 100).toInt()}%")
                    fileTransferStatus?.let { Text(it) }
                }
            },
            confirmButton = {}
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(onClick = { showErrorDialog = false }) {
                    Text("OK")
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