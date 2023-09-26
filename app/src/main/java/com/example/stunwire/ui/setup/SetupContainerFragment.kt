package com.example.stunwire.ui.setup

import androidx.fragment.app.viewModels
import com.example.stunwire.ui.StunWireFragment
import com.example.stunwire.viewmodel.SetupViewModel

abstract class SetupContainerFragment: StunWireFragment() {
    override val viewModel: SetupViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    protected val parent: SetupFragment by lazy {
        requireParentFragment().requireParentFragment() as SetupFragment
    }
}