package com.example.stunwire.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import com.example.stunwire.StunWireApp
import com.example.stunwire.model.db.SessionMessage
import com.example.stunwire.model.networking.SessionState
import com.example.stunwire.model.networking.SessionStatus
import kotlinx.coroutines.flow.Flow

class SessionViewModel: ViewModel() {
    val sessionState: LiveData<SessionState> = StunWireApp.instance.sessionState
    val sessionStatus: LiveData<SessionStatus>? = StunWireApp.instance.sessionService?.getSessionStatus()
    val sessionData: Flow<PagingData<SessionMessage>> = Pager(
        config = PagingConfig(
            pageSize = 10,
            enablePlaceholders = true,
            maxSize = 30
        )
    ) {
        StunWireApp.instance.sessionsRepo.getAllPaged()
    }
        .flow
        .cachedIn(viewModelScope)


    fun disconnectLaunched() {
        StunWireApp.instance.sessionService?.disconnectLaunched()
    }

    fun textChanged() {
        StunWireApp.instance.sessionService?.sendPeerTypingMessage()
    }

    fun sendTextMessage(text: String) {
        StunWireApp.instance.sessionService?.sendTextMessage(text)
    }

    fun sendImageMessage(uri: Uri) {
        StunWireApp.instance.sessionService?.sendImageMessage(uri)
    }

    fun deleteMessage(isSent: Boolean, messageCode: Int) {
        StunWireApp.instance.sessionsRepo.delete(isSent, messageCode)
        StunWireApp.instance.sessionService?.deleteMessage(isSent, messageCode)
    }
}