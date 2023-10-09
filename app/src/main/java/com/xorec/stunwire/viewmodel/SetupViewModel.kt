package com.xorec.stunwire.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import com.xorec.stunwire.PARTNER_ADDRESS_PREFERENCE_KEY
import com.xorec.stunwire.SHOULD_SAVE_PARTNER_ADDRESS_PREFERENCE_KEY
import com.xorec.stunwire.StunWireApp
import com.xorec.stunwire.model.networking.ADDRESS_RETRIEVED_KEY
import com.xorec.stunwire.model.networking.NetworkAddress
import com.xorec.stunwire.model.networking.SessionState

class SetupViewModel : ViewModel() {
    val sessionState: LiveData<SessionState> = StunWireApp.instance.sessionState
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(StunWireApp.instance.applicationContext)

    fun startSessionChosen() {
        StunWireApp.instance.startSessionChosen()
    }

    fun sessionTypeChosen(isInternetSession: Boolean) {
        StunWireApp.instance.launchSessionService(isInternetSession)
    }

    fun partnerAddressInputReceived(partnerAddress: String): Boolean {
        val regex = Regex("(?:[A-Za-z\\d-]+\\.)+[A-Za-z\\d]{1,3}:\\d{1,5}")

        partnerAddress.removePrefix("http://").also { shrunkPartnerAddress ->
            return if (shrunkPartnerAddress.matches(regex)) {
                if (preferences.getBoolean(SHOULD_SAVE_PARTNER_ADDRESS_PREFERENCE_KEY, false)) {
                    preferences.edit().putString(PARTNER_ADDRESS_PREFERENCE_KEY, shrunkPartnerAddress).apply()
                }
                StunWireApp.instance.startHandshake(NetworkAddress(shrunkPartnerAddress))
                true
            } else {
                false
            }
        }
    }

    fun getAddress(): String {
        return sessionState.value!!.bundle!!.getString(ADDRESS_RETRIEVED_KEY)!!
    }

    fun getPartnerAddress(): String {
        return if (preferences.getBoolean(SHOULD_SAVE_PARTNER_ADDRESS_PREFERENCE_KEY, false)) {
            preferences.getString(PARTNER_ADDRESS_PREFERENCE_KEY, "")!!
        } else ""
    }

    fun copyAddressToClipboard() {
        (ContextCompat.getSystemService(
            StunWireApp.instance,
            ClipboardManager::class.java
        ) as ClipboardManager)
            .setPrimaryClip(
                ClipData.newPlainText("StunWire partner address", sessionState.value!!.bundle!!.getString(
                    ADDRESS_RETRIEVED_KEY
                )))
    }

    fun setupCanceled() {
        StunWireApp.instance.setupCanceled()
    }
}