package com.example.stunwire.ui.setup.upper_container

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import com.example.stunwire.R
import com.example.stunwire.databinding.SetupUpperContainerLameFragmentBinding
import com.example.stunwire.ui.setup.SetupContainerFragment

const val LAME_REASON_ARG_KEY = "LAME_REASON"

class SetupLameFragment : SetupContainerFragment() {
    override lateinit var binding: SetupUpperContainerLameFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.setup_upper_container_lame_fragment, container, false)
        binding.setupUpperContainerLameFragmentTv.text = getString(requireArguments().getInt(LAME_REASON_ARG_KEY))
        return binding.root
    }
}