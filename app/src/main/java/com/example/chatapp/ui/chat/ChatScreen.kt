package com.example.chatapp.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chatapp.data.model.Message
import kotlinx.coroutines.launch

/**
 * 聊天界面的主要组件
 *
 * @param currentUserId 当前用户ID
 * @param otherUserId 聊天对象的用户ID
 * @param isReadOnly 是否为只读模式
 * @param viewModel 聊天界面的ViewModel
 * @param onBackClick 返回按钮点击回调
 */
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showOptionsDialog = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "选项")
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
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                    IconButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        enabled = chatState == ChatState.Active
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "发送文件")
                    }
                }
            }
        }
    }

    /**
     * 处理文件传输状态
     */
    when (val state = fileTransferState) {
        is FileTransferState.AwaitingConfirmation -> {
            AlertDialog(
                onDismissRequest = { viewModel.cancelFileTransfer() },
                title = { Text("确认文件传输") },
                text = { Text("您要发送文件：${state.fileName}吗？") },
                confirmButton = {
                    Button(onClick = { viewModel.confirmFileTransfer() }) {
                        Text("发送")
                    }
                },
                dismissButton = {
                    Button(onClick = { viewModel.cancelFileTransfer() }) {
                        Text("取消")
                    }
                }
            )
        }
        is FileTransferState.WaitingForAcceptance -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("等待接受") },
                text = { Text("等待对方接受文件传输...") },
                confirmButton = { }
            )
        }
        is FileTransferState.Sending -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("发送文件") },
                text = {
                    Column {
                        Text("正在发送：${state.fileName}")
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = { }
            )
        }
        is FileTransferState.Receiving -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("接收文件") },
                text = {
                    Column {
                        Text("正在接收：${state.fileName}")
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = { }
            )
        }
        is FileTransferState.ReceivingRequest -> {
            AlertDialog(
                onDismissRequest = { viewModel.rejectFileTransfer() },
                title = { Text("文件传输请求") },
                text = { Text("您要接收文件：${state.fileName}吗？") },
                confirmButton = {
                    Button(onClick = { viewModel.acceptFileTransfer() }) {
                        Text("接受")
                    }
                },
                dismissButton = {
                    Button(onClick = { viewModel.rejectFileTransfer() }) {
                        Text("拒绝")
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
                title = { Text("文件传输错误") },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = { viewModel.resetFileTransferState() }) {
                        Text("确定")
                    }
                }
            )
        }
        is FileTransferState.Idle -> {
            // 空闲状态，不显示对话框
        }
    }

    /**
     * 退出聊天确认对话框
     */
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("退出聊天") },
            text = { Text("您确定要退出聊天吗？") },
            confirmButton = {
                Button(onClick = {
                    showExitDialog = false
                    viewModel.endChat()
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                Button(onClick = { showExitDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    /**
     * 聊天结束提示对话框
     */
    if (chatState == ChatState.Ended) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("聊天已结束") },
            text = { Text("此次聊天已经结束。") },
            confirmButton = {
                Button(onClick = { onBackClick() }) {
                    Text("确定")
                }
            }
        )
    }

    /**
     * 聊天选项对话框
     */
    if (showOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text("聊天选项") },
            text = {
                Column {
                    Text("筛选消息:", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    FilterType.entries.forEach { filterType ->
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
                        Text("清除所有消息")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showOptionsDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

/**
 * 消息项组件
 *
 * @param message 消息对象
 * @param isCurrentUser 是否为当前用户的消息
 */
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
                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = "文件")
                        Text(text = "文件: ${message.content}")
                    }
                }
            }
        }
    }
}