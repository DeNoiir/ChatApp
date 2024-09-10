package com.example.chatapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.chatapp.ui.chat.ChatScreen
import com.example.chatapp.ui.login.LoginScreen
import com.example.chatapp.ui.settings.SettingsScreen
import com.example.chatapp.ui.theme.ChatAppTheme
import com.example.chatapp.ui.users.UsersScreen
import com.example.chatapp.ui.users.UsersViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // All permissions granted, proceed with app initialization
            initializeApp()
        } else {
            // Handle the case where permissions are not granted
            // You might want to show a dialog explaining why the permissions are necessary
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (checkPermissions()) {
            initializeApp()
        } else {
            requestPermissions()
        }
    }

    private fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun initializeApp() {
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

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
}

@Composable
fun ChatApp() {
    val navController = rememberNavController()
    val usersViewModel: UsersViewModel = hiltViewModel()

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
                viewModel = usersViewModel,
                onUserSelected = { selectedUserId, selectedUserIp ->
                    val encodedIp = URLEncoder.encode(selectedUserIp, StandardCharsets.UTF_8.toString())
                    navController.navigate("chat/$userId/$selectedUserId/$encodedIp")
                },
                onSettingsClick = {
                    navController.navigate("settings/$userId")
                }
            )
        }
        composable(
            route = "chat/{currentUserId}/{otherUserId}/{otherUserIp}",
            arguments = listOf(
                navArgument("currentUserId") { type = NavType.StringType },
                navArgument("otherUserId") { type = NavType.StringType },
                navArgument("otherUserIp") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val currentUserId = backStackEntry.arguments?.getString("currentUserId") ?: return@composable
            val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: return@composable
            val otherUserIp = backStackEntry.arguments?.getString("otherUserIp") ?: return@composable
            ChatScreen(
                currentUserId = currentUserId,
                otherUserId = otherUserId,
                otherUserIp = otherUserIp,
                onBackClick = {
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
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }

    navController.addOnDestinationChangedListener { _, destination, _ ->
        if (destination.route?.startsWith("users") == true) {
            usersViewModel.resetConnectionState()
        }
    }
}