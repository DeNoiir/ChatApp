package com.example.chatapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.chatapp.ui.chat.ChatScreen
import com.example.chatapp.ui.filetransfer.FileTransferScreen
import com.example.chatapp.ui.login.LoginScreen
import com.example.chatapp.ui.settings.SettingsScreen
import com.example.chatapp.ui.users.UsersScreen
import com.example.chatapp.ui.theme.ChatAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatApp()
                }
            }
        }
    }
}

@Composable
fun ChatApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { userId ->
                    navController.navigate("users/$userId") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("users/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            UsersScreen(
                onUserSelected = { selectedUserId ->
                    navController.navigate("chat/$userId/$selectedUserId")
                },
                onDeviceSelected = { deviceId ->
                    navController.navigate("chat/$userId/$deviceId")
                },
                onSettingsClick = {
                    navController.navigate("settings/$userId")
                }
            )
        }

        composable("chat/{currentUserId}/{otherUserId}") { backStackEntry ->
            val currentUserId = backStackEntry.arguments?.getString("currentUserId") ?: return@composable
            val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: return@composable
            ChatScreen(
                currentUserId = currentUserId,
                otherUserId = otherUserId
            )
        }
        composable("filetransfer/{currentUserId}/{receiverId}/{fileName}") { backStackEntry ->
            val currentUserId = backStackEntry.arguments?.getString("currentUserId") ?: return@composable
            val receiverId = backStackEntry.arguments?.getString("receiverId") ?: return@composable
            val fileName = backStackEntry.arguments?.getString("fileName") ?: return@composable
            FileTransferScreen(
                currentUserId = currentUserId,
                receiverId = receiverId,
                fileName = fileName,
                onTransferComplete = {
                    navController.popBackStack()
                }
            )
        }
        composable("settings/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            SettingsScreen(
                userId = userId,
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}