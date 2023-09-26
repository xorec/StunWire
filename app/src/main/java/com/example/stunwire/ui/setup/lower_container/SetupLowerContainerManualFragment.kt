package com.example.stunwire.ui.setup.lower_container

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.navigation.findNavController
import com.example.stunwire.R
import com.example.stunwire.databinding.SetupLowerContainerManualFragmentBinding
import com.example.stunwire.ui.setup.SetupContainerFragment

class SetupLowerContainerManualFragment : SetupContainerFragment() {
    override lateinit var binding: SetupLowerContainerManualFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater,
            R.layout.setup_lower_container_manual_fragment, container, false)

        binding.setupLowerContainerManualFragmentBtn.setOnClickListener {
            requireActivity().findNavController(R.id.nav_host_fragment).navigate(R.id.action_setupFragment_to_manualFragment)
        }

        return binding.root
    }
}