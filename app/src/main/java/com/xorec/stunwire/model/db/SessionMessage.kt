package com.xorec.stunwire.model.db

import androidx.room.Entity

@Entity(primaryKeys = ["isSent", "messageCode"])
data class SessionMessage (
    val isSent: Boolean,
    val messageCode: Int,
    val type: Byte,
    val status: SessionMessageStatus,
    var isReadSent: Boolean,
    val timestamp: Long,
    val data: ByteArray
)