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
import com.example.chatapp.ui.users.UsersScreen
import com.example.chatapp.ui.users.UsersViewModel
import com.example.chatapp.ui.theme.ChatAppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 主活动类
 * 负责应用程序的初始化、权限处理和主界面设置
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * 权限请求启动器
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            initializeApp()
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

    /**
     * 检查所需权限是否已被授予
     *
     * @return 如果所有所需权限都已被授予则返回true，否则返回false
     */
    private fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 请求所需权限
     */
    private fun requestPermissions() {
        requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    /**
     * 初始化应用程序
     * 设置主界面内容
     */
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
        /**
         * 应用程序所需的权限列表
         */
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE
        )
    }
}

/**
 * 聊天应用程序的主Composable函数
 * 设置应用程序的导航结构
 */
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
                onNavigateToChat = { otherUserId, isReadOnly ->
                    navController.navigate("chat/$userId/$otherUserId/$isReadOnly")
                },
                onSettingsClick = {
                    navController.navigate("settings/$userId")
                }
            )
        }
        composable(
            route = "chat/{currentUserId}/{otherUserId}/{isReadOnly}",
            arguments = listOf(
                navArgument("currentUserId") { type = NavType.StringType },
                navArgument("otherUserId") { type = NavType.StringType },
                navArgument("isReadOnly") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val currentUserId = backStackEntry.arguments?.getString("currentUserId") ?: return@composable
            val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: return@composable
            val isReadOnly = backStackEntry.arguments?.getBoolean("isReadOnly") ?: false
            ChatScreen(
                currentUserId = currentUserId,
                otherUserId = otherUserId,
                isReadOnly = isReadOnly,
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
}