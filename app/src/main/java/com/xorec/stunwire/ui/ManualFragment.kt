package com.xorec.stunwire.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.navigation.Navigation
import com.xorec.stunwire.R
import com.xorec.stunwire.databinding.ManualFragmentBinding

class ManualFragment : StunWireFragment() {
    override val viewModel: ViewModel by viewModels()
    override lateinit var binding: ManualFragmentBinding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.manual_fragment, container, false)

        binding.manualFragmentToolbar.setNavigationOnClickListener {
            Navigation.findNavController(it).popBackStack()
        }

        return binding.root
    }
}