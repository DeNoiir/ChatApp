package com.example.chatapp.network

/**
 * 网络消息封装类
 * 定义了各种类型的网络消息
 */
sealed class NetworkMessage {
    /**
     * 聊天消息
     *
     * @property content 消息内容
     */
    data class ChatMessage(val content: String) : NetworkMessage()

    /**
     * 文件传输请求
     *
     * @property fileName 文件名
     * @property fileSize 文件大小
     */
    data class FileTransferRequest(val fileName: String, val fileSize: Long) : NetworkMessage()

    /**
     * 文件传输响应
     *
     * @property accepted 是否接受传输
     */
    data class FileTransferResponse(val accepted: Boolean) : NetworkMessage()

    /**
     * 文件数据
     *
     * @property fileSize 文件大小
     * @property data 文件数据
     */
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

    /**
     * 文件传输完成通知
     *
     * @property fileName 文件名
     */
    data class FileTransferCompleted(val fileName: String) : NetworkMessage()

    /**
     * 文件接收通知
     *
     * @property fileName 文件名
     */
    data class FileReceivedNotification(val fileName: String) : NetworkMessage()
}

/**
 * 消息类型枚举
 * 定义了不同类型的消息及其对应的字节值
 *
 * @property value 类型对应的字节值
 */
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
        /**
         * 根据字节值获取对应的消息类型
         *
         * @param value 字节值
         * @return 对应的MessageType
         */
        fun fromByte(value: Byte) = entries.first { it.value == value }
    }
}