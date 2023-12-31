package com.xorec.stunwire.ui.setup.upper_container

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import com.xorec.stunwire.R
import com.xorec.stunwire.databinding.SetupUpperContainerTypeSelectionFragmentBinding
import com.xorec.stunwire.ui.setup.SetupContainerFragment

class SetupUpperContainerTypeSelectionFragment : SetupContainerFragment() {
    override lateinit var binding: SetupUpperContainerTypeSelectionFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater,
            R.layout.setup_upper_container_type_selection_fragment, container, false)

        binding.setupUpperContainerSessionTypeSelectionFragmentProceedBtn.setOnClickListener {
            viewModel.sessionTypeChosen(binding.setupUpperContainerSelectSessionTypeFragmentRg.checkedRadioButtonId
                    == binding.setupUpperContainerSessionTypeSelectionFragmentRbInternet.id)
            parent.move()
        }

        return binding.root
    }
}