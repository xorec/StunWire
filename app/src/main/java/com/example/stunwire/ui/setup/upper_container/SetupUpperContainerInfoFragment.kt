package com.example.stunwire.ui.setup.upper_container

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import com.example.stunwire.R
import com.example.stunwire.databinding.SetupUpperContainerInfoFragmentBinding
import com.example.stunwire.ui.setup.SetupContainerFragment

class SetupUpperContainerInfoFragment : SetupContainerFragment() {
    override lateinit var binding: SetupUpperContainerInfoFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.setup_upper_container_info_fragment, container, false)

        binding.setupUpperContainerInfoFragmentYourIdTil.setEndIconOnClickListener {
            viewModel.copyAddressToClipboard()
        }

        binding.setupUpperContainerInfoFragmentProceedBtn.setOnClickListener {
            if (viewModel.partnerAddressInputReceived(binding.setupUpperContainerInfoFragmentPartnerIdEt.text.toString())) {
                parent.move()
            } else binding.setupUpperContainerInfoFragmentPartnerIdEt.error = getString(R.string.setup_info_fragment_error)
        }

        return binding.root
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        binding.setupUpperContainerInfoFragmentYourIdEt.setText(viewModel.getAddress())
        binding.setupUpperContainerInfoFragmentPartnerIdEt.setText(viewModel.getPartnerAddress())
    }
}