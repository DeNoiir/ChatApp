package com.example.chatapp.ui.filetransfer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun FileTransferScreen(
    currentUserId: String,
    receiverId: String,
    fileName: String,
    viewModel: FileTransferViewModel = hiltViewModel(),
    onTransferComplete: () -> Unit
) {
    val progress by viewModel.transferProgress.collectAsState()

    LaunchedEffect(progress) {
        if (progress == 1f) {
            onTransferComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Transferring file: $fileName",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}