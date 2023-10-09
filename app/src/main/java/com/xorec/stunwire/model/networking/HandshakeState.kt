package com.xorec.stunwire.model.networking

enum class HandshakeState {
    HANDSHAKE_STATE_WAITING_FOR_HANDSHAKE_DATA,
    HANDSHAKE_STATE_HANDSHAKE_DATA_APPROVED,
    HANDSHAKE_STATE_HANDSHAKE_COMPLETED,
    HANDSHAKE_STATE_HANDSHAKE_ERROR,
}