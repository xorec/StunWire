package com.example.stunwire.viewmodel

import androidx.lifecycle.ViewModel

class SettingsViewModel: ViewModel() {
    fun checkServerInput(input: Any): Boolean {
        if (input !is String) {
            return false
        }

        return input.matches(Regex("(?:[A-Za-z\\d-]+\\.)+[A-Za-z\\d]{1,3}:\\d{1,5}"))
    }
}