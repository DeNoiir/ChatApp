package com.example.chatapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 聊天应用程序类
 * 作为应用程序的入口点,启用Hilt依赖注入
 */
@HiltAndroidApp
class ChatApplication : Application()