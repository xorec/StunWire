package com.example.stunwire.model.networking

data class ProtocolMessage(val messageType: EncryptedMessageType, val data: ByteArray?) {
}