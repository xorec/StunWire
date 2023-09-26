package com.example.stunwire.ui.setup.lower_container

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import com.example.stunwire.R
import com.example.stunwire.databinding.SetupLowerContainerCancelFragmentBinding
import com.example.stunwire.ui.setup.SetupContainerFragment

class SetupLowerContainerCancelFragment : SetupContainerFragment() {
    override lateinit var binding: SetupLowerContainerCancelFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: SetupLowerContainerCancelFragmentBinding = DataBindingUtil.inflate(inflater,
            R.layout.setup_lower_container_cancel_fragment, container, false)

        binding.setupLowerContainerCancelFragmentBtn.setOnClickListener {
            viewModel.setupCanceled()
            parent.move()
        }

        return binding.root
    }
}