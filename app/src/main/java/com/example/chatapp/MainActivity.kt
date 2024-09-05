// MainActivity.kt
package com.example.chatapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
        composable(
            route = "users/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            UsersScreen(
                userId = userId,
                onUserSelected = { selectedUserId ->
                    navController.navigate("chat/$userId/$selectedUserId")
                },
                onSettingsClick = {
                    navController.navigate("settings/$userId")
                }
            )
        }
        composable(
            route = "chat/{currentUserId}/{otherUserId}",
            arguments = listOf(
                navArgument("currentUserId") { type = NavType.StringType },
                navArgument("otherUserId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val currentUserId = backStackEntry.arguments?.getString("currentUserId") ?: return@composable
            val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: return@composable
            ChatScreen(
                currentUserId = currentUserId,
                otherUserId = otherUserId
            )
        }
        composable(
            route = "filetransfer/{currentUserId}/{receiverId}/{fileName}",
            arguments = listOf(
                navArgument("currentUserId") { type = NavType.StringType },
                navArgument("receiverId") { type = NavType.StringType },
                navArgument("fileName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
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
        composable(
            route = "settings/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            SettingsScreen(
                userId = userId,
                onLogout = {
                    ChatApplication.instance.stopDiscoveryServer()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}