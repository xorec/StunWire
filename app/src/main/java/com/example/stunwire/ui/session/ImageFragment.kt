package com.example.stunwire.ui.session

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.example.stunwire.R
import com.example.stunwire.StunWireApp
import com.example.stunwire.databinding.ImageFragmentBinding
import com.example.stunwire.ui.StunWireFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class ImageFragment : StunWireFragment() {
    override val viewModel: ViewModel by viewModels()
    override lateinit var binding: ImageFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.image_fragment, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            val message = StunWireApp.instance.sessionsRepo.get(
                requireArguments().getBoolean("isSent"),
                requireArguments().getInt("messageCode")
            )

            launch(Dispatchers.Main) {
                binding.imageFragmentIv.setImageBitmap(BitmapFactory.decodeByteArray(message.data, 0, message.data.size))
            }
        }

    }

    override fun onResume() {
        super.onResume()
        requireActivity().window.addFlags(FLAG_LAYOUT_NO_LIMITS)

    }

    override fun onStop() {
        requireActivity().window.clearFlags(FLAG_LAYOUT_NO_LIMITS)
        super.onStop()
    }

}