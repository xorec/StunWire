package com.xorec.stunwire.model.networking

data class ProtocolMessage(val messageType: EncryptedMessageType, val data: ByteArray?) {
}