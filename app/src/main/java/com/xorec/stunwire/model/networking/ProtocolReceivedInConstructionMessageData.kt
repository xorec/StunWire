package com.xorec.stunwire.model.networking

data class ProtocolReceivedInConstructionMessageData(val lastIndex: Int) {
    val chunks = HashMap<Int, ByteArray>()
}