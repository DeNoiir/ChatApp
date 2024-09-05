package com.example.chatapp.ui.filetransfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.model.Message
import com.example.chatapp.data.repository.MessageRepository
import com.example.chatapp.network.TcpCommunicationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FileTransferViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val tcpCommunicationService: TcpCommunicationService
) : ViewModel() {

    private val _transferProgress = MutableStateFlow(0f)
    val transferProgress: StateFlow<Float> = _transferProgress

    fun sendFile(senderId: String, receiverId: String, file: File) {
        viewModelScope.launch {
            // Simulating file transfer progress
            for (i in 1..100) {
                _transferProgress.value = i / 100f
                kotlinx.coroutines.delay(50) // Simulate transfer time
            }

            // After transfer is complete, save message to database
            val message = Message(
                sender = senderId,
                receiver = receiverId,
                type = 1, // 1 for file message
                content = file.name
            )
            messageRepository.insertMessage(message)

            // Reset progress
            _transferProgress.value = 0f
        }
    }
}