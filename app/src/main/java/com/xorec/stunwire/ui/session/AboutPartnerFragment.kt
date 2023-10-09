package com.xorec.stunwire.ui.session

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import com.xorec.stunwire.R
import com.xorec.stunwire.model.crypto.getPartnerIdentityPublicKeyString
import com.xorec.stunwire.model.crypto.getPartnerPublicKeyBitmap
import com.xorec.stunwire.databinding.AboutPartnerFragmentBinding
import com.xorec.stunwire.ui.StunWireFragment

class AboutPartnerFragment : StunWireFragment() {
    override val viewModel: ViewModel by viewModels()
    override lateinit var binding: AboutPartnerFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater,
            R.layout.about_partner_fragment, container, false)
        binding.aboutPartnerFragmentToolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }
        binding.aboutPartnerFragmentPartnerKeyIv.setImageBitmap(getPartnerPublicKeyBitmap())
        binding.aboutPartnerFragmentKeyFullVersionTv.text = getPartnerIdentityPublicKeyString()

        return binding.root
    }
}