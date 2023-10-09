package com.xorec.stunwire.ui.setup

import androidx.fragment.app.viewModels
import com.xorec.stunwire.ui.StunWireFragment
import com.xorec.stunwire.viewmodel.SetupViewModel

abstract class SetupContainerFragment: StunWireFragment() {
    override val viewModel: SetupViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    protected val parent: SetupFragment by lazy {
        requireParentFragment().requireParentFragment() as SetupFragment
    }
}