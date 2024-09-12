package com.example.chatapp.ui.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chatapp.data.model.User

/**
 * 用户列表界面的主要组件
 *
 * @param userId 当前用户ID
 * @param viewModel 用户列表界面的ViewModel
 * @param onNavigateToChat 导航到聊天界面的回调函数
 * @param onSettingsClick 设置按钮点击回调
 */
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
            onNavigateToChat(otherUserId, false)  // false 表示不是只读模式
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("聊天应用") },
                actions = {
                    IconButton(onClick = { viewModel.discoverDevices() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "发现设备")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
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
                    "发现的用户",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(discoveredUsers) { user ->
                UserItem(
                    user = user,
                    icon = Icons.Default.PersonAdd,
                    iconDescription = "新用户",
                    onClick = { viewModel.sendChatInvitation(user.id) }
                )
            }
            item {
                Text(
                    "已知用户",
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
                    iconDescription = "聊天历史",
                    onClick = { onNavigateToChat(user.id, true) }  // true 表示只读模式
                )
            }
        }
    }

    /**
     * 处理聊天邀请状态
     */
    when (val state = chatInvitationState) {
        is ChatInvitationState.Sent -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetChatInvitationState() },
                title = { Text("等待响应") },
                text = { Text("正在等待对方接受您的邀请...") },
                confirmButton = {}
            )
        }
        is ChatInvitationState.Received -> {
            AlertDialog(
                onDismissRequest = { viewModel.rejectChatInvitation() },
                title = { Text("聊天邀请") },
                text = { Text("${state.userName} 想要与您聊天。") },
                confirmButton = {
                    Button(onClick = { viewModel.acceptChatInvitation() }) {
                        Text("接受")
                    }
                },
                dismissButton = {
                    Button(onClick = { viewModel.rejectChatInvitation() }) {
                        Text("拒绝")
                    }
                }
            )
        }
        is ChatInvitationState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetChatInvitationState() },
                title = { Text("错误") },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = { viewModel.resetChatInvitationState() }) {
                        Text("确定")
                    }
                }
            )
        }
        else -> {} // 处理其他状态（如果需要）
    }

    /**
     * 显示邀请被拒绝的对话框
     */
    if (showRejectionDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRejectionDialog() },
            title = { Text("邀请被拒绝") },
            text = { Text("对方已拒绝您的聊天邀请。") },
            confirmButton = {
                Button(onClick = { viewModel.dismissRejectionDialog() }) {
                    Text("确定")
                }
            }
        )
    }
}

/**
 * 用户项组件
 *
 * @param user 用户对象
 * @param icon 图标
 * @param iconDescription 图标描述
 * @param onClick 点击回调
 */
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