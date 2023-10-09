package com.xorec.stunwire.model.networking

import android.os.Bundle

const val ADDRESS_RETRIEVED_KEY = "ADDRESS_RETRIEVED_KEY"

data class SessionState(val state: SessionStates, val bundle: Bundle?)