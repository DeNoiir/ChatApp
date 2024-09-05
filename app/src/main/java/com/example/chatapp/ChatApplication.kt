// ChatApplication.kt
package com.example.chatapp

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.chatapp.network.UdpDiscoveryService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@HiltAndroidApp
class ChatApplication : Application() {

    @Inject
    lateinit var udpDiscoveryService: UdpDiscoveryService

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USER_NAME_KEY = stringPreferencesKey("user_name")

        lateinit var instance: ChatApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        applicationScope.launch {
            val userId = getUserId()
            val userName = getUserName()
            if (userId != null && userName != null) {
                startDiscoveryServer(userId, userName)
            }
        }
    }

    private suspend fun getUserId(): String? {
        return dataStore.data.map { preferences ->
            preferences[USER_ID_KEY]
        }.first()
    }

    private suspend fun getUserName(): String? {
        return dataStore.data.map { preferences ->
            preferences[USER_NAME_KEY]
        }.first()
    }

    suspend fun setUserInfo(userId: String, userName: String) {
        dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = userId
            preferences[USER_NAME_KEY] = userName
        }
        startDiscoveryServer(userId, userName)
    }

    private fun startDiscoveryServer(userId: String, userName: String) {
        udpDiscoveryService.startDiscoveryServer(userId, userName)
    }

    fun stopDiscoveryServer() {
        udpDiscoveryService.stopDiscoveryServer()
    }

    override fun onTerminate() {
        stopDiscoveryServer()
        super.onTerminate()
    }
}