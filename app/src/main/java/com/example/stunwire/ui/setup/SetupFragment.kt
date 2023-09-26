package com.example.stunwire.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.example.stunwire.*
import com.example.stunwire.databinding.SetupFragmentBinding
import com.example.stunwire.model.networking.SessionStates
import com.example.stunwire.ui.*
import com.example.stunwire.ui.setup.upper_container.LAME_REASON_ARG_KEY
import com.example.stunwire.ui.setup.upper_container.LOADING_FRAGMENT_STRING_ARG_KEY
import com.example.stunwire.viewmodel.SetupViewModel

class SetupFragment : StunWireFragment() {
    override val viewModel: SetupViewModel by viewModels()
    override lateinit var binding: SetupFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.setup_fragment, container, false)

        binding.setupFragmentSettingsIb.setOnClickListener {
            Navigation.findNavController(it).navigate(R.id.action_setupFragment_to_settingsFragment)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val upperController = binding.setupFragmentUpperFcv.getFragment<NavHostFragment>().navController
        val lowerController = binding.setupFragmentLowerFcv.getFragment<NavHostFragment>().navController

        when (viewModel.sessionState.value!!.state) {
            SessionStates.SESSION_STATE_NO_SETUP -> {
                if (upperController.currentDestination!!.id != R.id.setupUpperContainerChooseActionFragment) {
                    upperController.popBackStack()
                    lowerController.popBackStack()
                }
            }
            SessionStates.SESSION_STATE_RETRIEVING_ADDRESS -> {
                if (upperController.currentDestination!!.id != R.id.setupUpperContainerLoadingFragment) {
                    upperController.navigate(R.id.action_setupUpperContainerChooseActionFragment_to_setupUpperContainerTypeSelectionFragment)
                    upperController.navigate(R.id.action_setupUpperContainerTypeSelectionFragment_to_setupUpperContainerLoadingFragment,
                        bundleOf(Pair(
                            LOADING_FRAGMENT_STRING_ARG_KEY,
                            R.string.setup_upper_container_loading_fragment_waiting_for_results
                        )))
                    lowerController.navigate(R.id.action_setupLowerContainerManualFragment_to_setupLowerContainerCancelFragment)
                }
            }
            SessionStates.SESSION_STATE_RETRIEVING_ADDRESS_ERROR -> {
                if (upperController.currentDestination!!.id != R.id.setupUpperContainerLameFragment) {
                    upperController.navigate(R.id.action_setupUpperContainerChooseActionFragment_to_setupUpperContainerTypeSelectionFragment)
                    upperController.navigate(R.id.action_setupUpperContainerTypeSelectionFragment_to_setupUpperContainerLoadingFragment,
                        bundleOf(Pair(
                            LOADING_FRAGMENT_STRING_ARG_KEY,
                            R.string.setup_upper_container_loading_fragment_waiting_for_results
                        )))
                    upperController.navigate(R.id.action_setupUpperContainerLoadingFragment_to_setupUpperContainerLameFragment,
                        bundleOf(Pair(
                            LAME_REASON_ARG_KEY,
                            R.string.lame_fragment_reason_primary_stun_server_does_not_respond
                        )))
                    lowerController.navigate(R.id.action_setupLowerContainerManualFragment_to_setupLowerContainerCancelFragment)
                }
            }
            SessionStates.SESSION_STATE_ADDRESS_RETRIEVED -> {
                if (upperController.currentDestination!!.id != R.id.setupUpperContainerInfoFragment) {
                    upperController.navigate(R.id.action_setupUpperContainerChooseActionFragment_to_setupUpperContainerTypeSelectionFragment)
                    upperController.navigate(R.id.action_setupUpperContainerTypeSelectionFragment_to_setupUpperContainerLoadingFragment,
                        bundleOf(Pair(
                            LOADING_FRAGMENT_STRING_ARG_KEY,
                            R.string.setup_upper_container_loading_fragment_waiting_for_results
                        )))
                    upperController.navigate(R.id.action_setupUpperContainerLoadingFragment_to_setupUpperContainerInfoFragment)
                    lowerController.navigate(R.id.action_setupLowerContainerManualFragment_to_setupLowerContainerCancelFragment)
                }
            }
            SessionStates.SESSION_STATE_PERFORMING_HANDSHAKE -> {
                if (upperController.currentDestination!!.id != R.id.setupUpperContainerLoadingFragment) {
                    upperController.navigate(R.id.action_setupUpperContainerChooseActionFragment_to_setupUpperContainerTypeSelectionFragment)
                    upperController.navigate(R.id.action_setupUpperContainerTypeSelectionFragment_to_setupUpperContainerLoadingFragment,
                        bundleOf(Pair(
                            LOADING_FRAGMENT_STRING_ARG_KEY,
                            R.string.setup_upper_container_loading_fragment_waiting_for_results
                        )))
                    upperController.navigate(R.id.action_setupUpperContainerLoadingFragment_to_setupUpperContainerInfoFragment)
                    upperController.navigate(R.id.action_setupUpperContainerInfoFragment_to_setupUpperContainerLoadingFragment,
                        bundleOf(Pair(
                            LOADING_FRAGMENT_STRING_ARG_KEY,
                            R.string.loading_fragment_connecting_to_partner
                        )))
                    lowerController.navigate(R.id.action_setupLowerContainerManualFragment_to_setupLowerContainerCancelFragment)
                }
            }
            else -> {}
        }
    }

    fun move() {
        val upperController = binding.setupFragmentUpperFcv.findNavController()
        val lowerController = binding.setupFragmentLowerFcv.findNavController()

        when (viewModel.sessionState.value!!.state) {
            SessionStates.SESSION_STATE_NO_SETUP -> {
                upperController.popBackStack()
                lowerController.popBackStack()
            }
            SessionStates.SESSION_STATE_TYPE_SELECTION -> {
                upperController
                    .navigate(R.id.action_setupUpperContainerChooseActionFragment_to_setupUpperContainerTypeSelectionFragment)
                lowerController
                    .navigate(R.id.action_setupLowerContainerManualFragment_to_setupLowerContainerCancelFragment)
            }
            SessionStates.SESSION_STATE_RETRIEVING_ADDRESS -> {
                upperController
                    .navigate(R.id.action_setupUpperContainerTypeSelectionFragment_to_setupUpperContainerLoadingFragment,
                    bundleOf(Pair(
                        LOADING_FRAGMENT_STRING_ARG_KEY,
                        R.string.setup_upper_container_loading_fragment_waiting_for_results
                    )))
            }
            SessionStates.SESSION_STATE_RETRIEVING_ADDRESS_ERROR -> {
                upperController
                    .navigate(R.id.action_setupUpperContainerLoadingFragment_to_setupUpperContainerLameFragment,
                        bundleOf(Pair(
                            LAME_REASON_ARG_KEY,
                            R.string.lame_fragment_reason_primary_stun_server_does_not_respond
                        )))
            }
            SessionStates.SESSION_STATE_ADDRESS_RETRIEVED -> {
                upperController
                    .navigate(R.id.action_setupUpperContainerLoadingFragment_to_setupUpperContainerInfoFragment)
            }
            SessionStates.SESSION_STATE_PERFORMING_HANDSHAKE -> {
                upperController
                    .navigate(R.id.action_setupUpperContainerInfoFragment_to_setupUpperContainerLoadingFragment,
                        bundleOf(Pair(
                            LOADING_FRAGMENT_STRING_ARG_KEY,
                            R.string.loading_fragment_connecting_to_partner
                        )))
            }
            SessionStates.SESSION_STATE_SESSION_ACTIVE -> {
                requireActivity().findNavController(R.id.nav_host_fragment).navigate(R.id.action_setupFragment_to_sessionFragment)
            }
            else -> {}
        }
    }
}