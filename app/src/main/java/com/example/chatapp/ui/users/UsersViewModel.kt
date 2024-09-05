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

    private val _discoveredUsers = MutableStateFlow<List<User>>(emptyList())
    val discoveredUsers: StateFlow<List<User>> = _discoveredUsers

    private lateinit var currentUser: User

    fun initialize(userId: String) {
        viewModelScope.launch {
            currentUser = userRepository.getUserById(userId) ?: return@launch
            loadUsers()
            startDiscoveryServer()
        }
    }

    private fun loadUsers() {
        viewModelScope.launch {
            userRepository.getAllUsers().collect { userList ->
                _users.value = userList.filter { it.id != currentUser.id }
            }
        }
    }

    fun discoverDevices() {
        viewModelScope.launch {
            val discoveredDevices = udpDiscoveryService.discoverDevices(currentUser.id, currentUser.name)
            val newUsers = discoveredDevices.map { (id, name) ->
                userRepository.insertOrUpdateUser(id, name)
                User(id = id, name = name)
            }
            _discoveredUsers.value = newUsers
        }
    }

    private fun startDiscoveryServer() {
        udpDiscoveryService.startDiscoveryServer(currentUser.id, currentUser.name)
    }

    override fun onCleared() {
        super.onCleared()
        udpDiscoveryService.stopDiscoveryServer()
    }
}