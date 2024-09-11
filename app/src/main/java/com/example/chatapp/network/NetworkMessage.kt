package com.example.chatapp.network

sealed class NetworkMessage {
    data class ChatMessage(val content: String) : NetworkMessage()
    data class FileTransferRequest(val fileName: String, val fileSize: Long) : NetworkMessage()
    data class FileTransferResponse(val accepted: Boolean) : NetworkMessage()
    data class FileData(val fileSize: Long, val data: ByteArray) : NetworkMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as FileData

            if (fileSize != other.fileSize) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = fileSize.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    data class FileTransferCompleted(val fileName: String) : NetworkMessage()
    data class FileReceivedNotification(val fileName: String) : NetworkMessage()
}

enum class MessageType(val value: Byte) {
    CHAT_MESSAGE(0x01),
    FILE_TRANSFER_REQUEST(0x02),
    FILE_TRANSFER_RESPONSE(0x03),
    FILE_DATA(0x04),
    END_CHAT(0x05),
    CHAT_INVITATION(0x06),
    CHAT_INVITATION_RESPONSE(0x07),
    FILE_TRANSFER_COMPLETED(0x08),
    FILE_RECEIVED_NOTIFICATION(0x09);

    companion object {
        fun fromByte(value: Byte) = entries.first { it.value == value }
    }
}