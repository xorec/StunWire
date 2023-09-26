package com.example.stunwire.ui.settings

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation
import com.example.stunwire.R
import com.example.stunwire.databinding.SettingsFragmentBinding
import com.example.stunwire.ui.StunWireFragment
import com.example.stunwire.viewmodel.SettingsViewModel

class SettingsFragment : StunWireFragment() {
    override val viewModel: SettingsViewModel by viewModels()
    override lateinit var binding: SettingsFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.settings_fragment, container, false)

        binding.settingsFragmentToolbar.setNavigationOnClickListener {
            Navigation.findNavController(it).popBackStack()
        }

        return binding.root
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        if (binding.settingsFragmentFcv.getFragment<Fragment>() == null) {
            childFragmentManager
                .beginTransaction()
                .add(R.id.settings_fragment_fcv, PreferenceFragment::class.java, null)
                .commit()
        }
    }
}