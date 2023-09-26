package com.example.stunwire.ui.setup.upper_container

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import com.example.stunwire.R
import com.example.stunwire.StunWireApp
import com.example.stunwire.databinding.SetupUpperContainerLoadingFragmentBinding
import com.example.stunwire.model.networking.SessionStates
import com.example.stunwire.ui.setup.SetupContainerFragment

const val LOADING_FRAGMENT_STRING_ARG_KEY = "LOADING_STRING_RESOURCE_ID"

class SetupLoadingFragment : SetupContainerFragment() {
    override lateinit var binding: SetupUpperContainerLoadingFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.setup_upper_container_loading_fragment, container, false)
        binding.setupUpperContainerLoadingFragmentTv.text = getString(requireArguments().getInt(LOADING_FRAGMENT_STRING_ARG_KEY))

        StunWireApp.instance.sessionState.observe(viewLifecycleOwner) { state ->
            if (state.state == SessionStates.SESSION_STATE_RETRIEVING_ADDRESS_ERROR ||
                    state.state == SessionStates.SESSION_STATE_ADDRESS_RETRIEVED ||
                    state.state == SessionStates.SESSION_STATE_SESSION_ACTIVE) {
                parent.move()
            }
        }

        return binding.root
    }
}