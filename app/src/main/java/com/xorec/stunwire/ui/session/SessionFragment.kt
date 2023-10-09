package com.xorec.stunwire.ui.session

import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.xorec.stunwire.R
import com.xorec.stunwire.StunWireApp
import com.xorec.stunwire.databinding.SessionFragmentBinding
import com.xorec.stunwire.model.networking.SessionStates
import com.xorec.stunwire.model.networking.SessionStatus
import com.xorec.stunwire.ui.StunWireFragment
import com.xorec.stunwire.viewmodel.SessionViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SessionFragment : StunWireFragment() {
    override val viewModel: SessionViewModel by viewModels()
    override lateinit var binding: SessionFragmentBinding
    private lateinit var onBackPressedCallback: OnBackPressedCallback
    private lateinit var pickMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.sessionState.value!!.state == SessionStates.SESSION_STATE_NO_SETUP) {
                    requireActivity().findNavController(R.id.nav_host_fragment).popBackStack()
                } else {
                    LogoutDialogFragment().show(childFragmentManager, LogoutDialogFragment.TAG)
                }
            }
        }

        pickMediaLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                viewModel.sendImageMessage(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.session_fragment, container, false)

        (binding.sessionFragmentRv.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        val adapter = SessionMessageListAdapter()

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)

                if (itemCount == 1) {
                    binding.sessionFragmentRv.smoothScrollToPosition(adapter.itemCount - 1)
                }
            }
        })

        binding.sessionFragmentRv.adapter = adapter

        ItemTouchHelper(object: ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return if (viewHolder.itemViewType == SessionMessageListAdapter.ViewHolderType.VIEWHOLDER_TYPE_TEXT_SENT.ordinal
                    || viewHolder.itemViewType == SessionMessageListAdapter.ViewHolderType.VIEWHOLDER_TYPE_IMAGE_SENT.ordinal) {
                    makeMovementFlags(0, ItemTouchHelper.LEFT)
                } else makeMovementFlags(0, ItemTouchHelper.RIGHT)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                viewModel.deleteMessage((viewHolder as SessionMessageListAdapter.SessionMessageViewHolder).getMessageInfo().first,
                    viewHolder.getMessageInfo().second)
            }

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
                return 0.85f
            }

        }).attachToRecyclerView(binding.sessionFragmentRv)

        lifecycleScope.launch {
            viewModel.sessionData.collectLatest {
                adapter.submitData(it)
            }
        }


        binding.sessionFragmentToolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        binding.sessionFragmentSendBtn.setOnClickListener {

            if (binding.sessionFragmentEt.text!!.isBlank()) {
                pickMediaLauncher.launch(PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    .build())
            } else {
                viewModel.sendTextMessage(binding.sessionFragmentEt.text.toString())
                binding.sessionFragmentEt.text!!.clear()
            }
        }

        if (viewModel.sessionState.value!!.state == SessionStates.SESSION_STATE_SESSION_ACTIVE) {
            showBottomPanel()

            binding.sessionFragmentToolbar.menu.findItem(R.id.session_fragment_toolbar_menu_item_about_partner).isVisible =
                true
            binding.sessionFragmentToolbar.menu.findItem(R.id.session_fragment_toolbar_menu_item_logout).isVisible =
                true

            binding.sessionFragmentToolbar.setOnMenuItemClickListener {
                when (it.itemId)  {
                    R.id.session_fragment_toolbar_menu_item_logout -> {
                        viewModel.disconnectLaunched()

                        true
                    }
                    R.id.session_fragment_toolbar_menu_item_about_partner -> {
                        activity?.findNavController(R.id.nav_host_fragment)?.navigate(R.id.action_sessionFragment_to_aboutPartnerFragment)
                        true
                    }
                    else -> {
                        false
                    }
                }
            }

            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)

            viewModel.sessionState.observe(viewLifecycleOwner) { setupStateEvent ->
                if (setupStateEvent.state == SessionStates.SESSION_STATE_NO_SETUP) {
                    binding.sessionFragmentToolbar.setTitle(R.string.session_fragment_toolbar_disconnected)
                    hideBottomPanel()
                    binding.sessionFragmentToolbar.setNavigationIcon(R.drawable.ic_arrow_back)
                    binding.sessionFragmentToolbar.menu.findItem(R.id.session_fragment_toolbar_menu_item_logout).isVisible =
                        false
                }
            }

            viewModel.sessionStatus?.observe(viewLifecycleOwner) { sessionStatus ->
                when(sessionStatus) {
                    SessionStatus.SESSION_STATUS_ACTIVE -> {
                        binding.sessionFragmentToolbar.setTitle(R.string.session_fragment_you_are_connected)
                    }
                    SessionStatus.SESSION_STATUS_TYPING -> {
                        binding.sessionFragmentToolbar.setTitle(R.string.session_fragment_toolbar_peer_typing)
                    }
                    SessionStatus.SESSION_STATUS_DISCONNECTING -> {
                        binding.sessionFragmentToolbar.setTitle(R.string.session_fragment_toolbar_disconnecting)
                        hideBottomPanel()
                    }
                }
            }

        } else {
            binding.sessionFragmentToolbar.title = StunWireApp.instance.sessionsRepo.getDatabaseName()
            binding.sessionFragmentToolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        }

        binding.sessionFragmentEt.doOnTextChanged { _, start, before, count ->
            if (count == 1) {
                viewModel.textChanged()
            }

            if (start == 0 && before == 0 && count != 0) {
                binding.sessionFragmentSendBtn.setImageResource(R.drawable.attach_to_send)
                (binding.sessionFragmentSendBtn.drawable as AnimatedVectorDrawable).start()
            } else if (start == 0 && count == 0 && before != 0) {
                binding.sessionFragmentSendBtn.setImageResource(R.drawable.send_to_attach)
                (binding.sessionFragmentSendBtn.drawable as AnimatedVectorDrawable).start()
            }
        }

        return binding.root

    }

    private fun hideBottomPanel() {
        binding.sessionFragmentChatDividerView.visibility = GONE
        binding.sessionFragmentBottomPanelLl.visibility = GONE
    }

    private fun showBottomPanel() {
        binding.sessionFragmentChatDividerView.visibility = VISIBLE
        binding.sessionFragmentBottomPanelLl.visibility = VISIBLE
    }
}