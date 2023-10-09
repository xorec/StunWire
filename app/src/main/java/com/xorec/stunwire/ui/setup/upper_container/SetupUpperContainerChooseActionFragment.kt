package com.xorec.stunwire.ui.setup.upper_container

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.navigation.findNavController
import com.xorec.stunwire.R
import com.xorec.stunwire.databinding.SetupUpperContainerChooseActionFragmentBinding
import com.xorec.stunwire.ui.setup.SetupContainerFragment

class SetupUpperContainerChooseActionFragment : SetupContainerFragment() {
    override lateinit var binding: SetupUpperContainerChooseActionFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: SetupUpperContainerChooseActionFragmentBinding = DataBindingUtil.inflate(inflater,
            R.layout.setup_upper_container_choose_action_fragment, container, false)

        binding.setupUpperContainerChooseActionFragmentStartSessionCv.setOnClickListener {
            viewModel.startSessionChosen()
            parent.move()
        }

        binding.setupUpperContainerChooseActionFragmentSavedSessionsCv.setOnClickListener {
            requireActivity().findNavController(R.id.nav_host_fragment).navigate(R.id.action_setupFragment_to_sessionsListFragment)
        }

        return binding.root
    }
}