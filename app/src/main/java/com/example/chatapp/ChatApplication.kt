package com.example.chatapp

import android.app.Application
import com.example.chatapp.network.UdpDiscoveryService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ChatApplication : Application() {

    @Inject
    lateinit var udpDiscoveryService: UdpDiscoveryService

    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startDiscoveryServer()
    }

    private fun startDiscoveryServer() {
        applicationScope.launch {
            udpDiscoveryService.startDiscoveryServer()
        }
    }

    override fun onTerminate() {
        udpDiscoveryService.stopDiscoveryServer()
        super.onTerminate()
    }
}