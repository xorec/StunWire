package com.xorec.stunwire

import android.app.Application
import android.content.*
import android.os.Build
import android.os.IBinder
import androidx.core.os.bundleOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import com.xorec.stunwire.model.crypto.doesMyIdentityKeyPairExist
import com.xorec.stunwire.model.crypto.refreshMyIdentityKeyPair
import com.xorec.stunwire.model.db.SessionsRepository
import com.xorec.stunwire.model.networking.ADDRESS_RETRIEVED_KEY
import com.xorec.stunwire.model.networking.NetworkAddress
import com.xorec.stunwire.model.networking.SessionService
import com.xorec.stunwire.model.networking.SessionState
import com.xorec.stunwire.model.networking.SessionStates
import com.xorec.stunwire.model.networking.SessionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.spongycastle.jce.provider.BouncyCastleProvider
import java.security.Security

const val STUN_SERVER_PREFERENCE_KEY = "STUN_SERVER"
const val SHOULD_SAVE_PARTNER_ADDRESS_PREFERENCE_KEY = "SHOULD_SAVE_PARTNER_ADDRESS"
const val PARTNER_ADDRESS_PREFERENCE_KEY = "PARTNER_ADDRESS"
const val PUBLIC_KEY_PATH = "/images/identity_key_bitmap"

class StunWireApp: Application() {
    private val scope = MainScope()

    private val _sessionState: MutableLiveData<SessionState> = MutableLiveData<SessionState>(
        SessionState(SessionStates.SESSION_STATE_NO_SETUP, null)
    )
    val sessionState: LiveData<SessionState> = _sessionState

    var sessionService: SessionService? = null

    private val sessionServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            sessionService = (service as SessionService.SessionServiceBinder).getService()

            scope.launch(Dispatchers.IO) {
                val result = sessionService!!.retrieveNetworkInfo()

                if (result == null) {
                    _sessionState.postValue(SessionState(SessionStates.SESSION_STATE_RETRIEVING_ADDRESS_ERROR, null))
                } else _sessionState.postValue(
                    SessionState(
                        SessionStates.SESSION_STATE_ADDRESS_RETRIEVED, bundleOf(Pair(
                    ADDRESS_RETRIEVED_KEY, result.toString())))
                )
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            sessionService = null
        }
    }

    lateinit var sessionsRepo: SessionsRepository
        private set

    companion object {
        lateinit var instance: StunWireApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        sessionsRepo = SessionsRepository.getInstance(this, scope)

        Security.addProvider(BouncyCastleProvider())

        PreferenceManager.getDefaultSharedPreferences(this).also { preferences ->
            if (!preferences.contains(STUN_SERVER_PREFERENCE_KEY)) {
                preferences.edit().putString(STUN_SERVER_PREFERENCE_KEY, "stun.l.google.com:19302").apply()
            }

            if (!preferences.contains(SHOULD_SAVE_PARTNER_ADDRESS_PREFERENCE_KEY)) {
                preferences.edit().putBoolean(SHOULD_SAVE_PARTNER_ADDRESS_PREFERENCE_KEY, true).apply()
            }
        }

        if (!doesMyIdentityKeyPairExist()) {
            refreshMyIdentityKeyPair(applicationContext)
        }
    }

    fun launchSessionService(isInternetSession: Boolean) {
        _sessionState.value = SessionState(SessionStates.SESSION_STATE_RETRIEVING_ADDRESS, null)

        Intent(applicationContext, SessionService::class.java).let {
            it.action = if (isInternetSession) {
                SessionType.SESSION_TYPE_INTERNET.name
            } else SessionType.SESSION_TYPE_LAN.name

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                startService(it)
            } else startForegroundService(it)
            bindService(it, sessionServiceConnection, 0)
        }
    }

    fun startSessionChosen() {
        _sessionState.value = SessionState(SessionStates.SESSION_STATE_TYPE_SELECTION, null)
    }


    fun startHandshake(partnerAddress: NetworkAddress) {
        _sessionState.value = SessionState(SessionStates.SESSION_STATE_PERFORMING_HANDSHAKE, null)
        sessionService!!.performHandshake(partnerAddress)
    }


    fun handshakeError() {
        _sessionState.value = SessionState(SessionStates.SESSION_STATE_HANDSHAKE_ERROR, null)
    }

    fun handshakeCompleted() {
        sessionsRepo.openSessionDatabase(createSessionDatabaseName())
        _sessionState.postValue(SessionState(SessionStates.SESSION_STATE_SESSION_ACTIVE, null))
    }

    fun setupCanceled() {
        stopSessionService()
    }

    fun sessionOver() {
        scope.launch(Dispatchers.Main) {
            stopSessionService()
        }
    }

    private fun stopSessionService() {
        _sessionState.value = SessionState(SessionStates.SESSION_STATE_NO_SETUP, null)
        sessionService?.let {
            unbindService(sessionServiceConnection)
            stopService(Intent(applicationContext, SessionService::class.java))
            sessionService = null
        }
    }
}