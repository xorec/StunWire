package com.xorec.stunwire.ui

import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel

abstract class StunWireFragment: Fragment() {
    protected abstract val viewModel: ViewModel
    protected abstract val binding: ViewDataBinding
}