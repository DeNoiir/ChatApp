package com.example.chatapp.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.model.User
import com.example.chatapp.data.repository.UserRepository
import com.example.chatapp.network.UdpDiscoveryService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val udpDiscoveryService: UdpDiscoveryService
) : ViewModel() {

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    private val _discoveredDevices = MutableStateFlow<List<String>>(emptyList())
    val discoveredDevices: StateFlow<List<String>> = _discoveredDevices

    init {
        loadUsers()
        discoverDevices()
    }

    private fun loadUsers() {
        viewModelScope.launch {
            userRepository.getAllUsers().collect {
                _users.value = it
            }
        }
    }

    fun discoverDevices() {
        viewModelScope.launch {
            _discoveredDevices.value = udpDiscoveryService.discoverDevices()
        }
    }
}