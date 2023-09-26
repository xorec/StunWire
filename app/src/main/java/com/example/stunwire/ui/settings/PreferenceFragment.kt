package com.example.stunwire.ui.settings

import android.os.Bundle
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.fragment.app.viewModels
import androidx.preference.*
import com.example.stunwire.SHOULD_SAVE_PARTNER_ADDRESS_PREFERENCE_KEY
import com.example.stunwire.STUN_SERVER_PREFERENCE_KEY
import com.example.stunwire.viewmodel.SettingsViewModel
import com.example.stunwire.R

class PreferenceFragment : PreferenceFragmentCompat() {
    private val viewModel: SettingsViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_screen, rootKey)

        findPreference<EditTextPreference>(
            STUN_SERVER_PREFERENCE_KEY
        )?.setOnPreferenceChangeListener { _, input ->
            if (viewModel.checkServerInput(input)) {
                true
            } else {
                Toast
                    .makeText(activity, getString(R.string.preference_fragment_check_input), LENGTH_LONG)
                    .show()
                false
            }
        }

        findPreference<SwitchPreferenceCompat>(
            SHOULD_SAVE_PARTNER_ADDRESS_PREFERENCE_KEY
        )?.also {
            it.summaryOn = getString(R.string.preference_screen_partner_id_will_be_filled_automatically)
            it.summaryOff = getString(R.string.preference_screen_partner_id_saving_disabled)
        }
    }
}
