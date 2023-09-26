package com.example.stunwire.ui.saved_sessions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import com.example.stunwire.R
import com.example.stunwire.StunWireApp
import com.example.stunwire.databinding.SessionsListFragmentBinding
import com.example.stunwire.ui.StunWireFragment
import com.example.stunwire.viewmodel.SessionsListViewModel

class SessionsListFragment : StunWireFragment() {
    override val viewModel: SessionsListViewModel by viewModels()
    override lateinit var binding: SessionsListFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = DataBindingUtil.inflate(inflater,
            R.layout.sessions_list_fragment, container, false)

        binding.sessionsListFragmentRv.adapter = SessionsListAdapter()

        val selectionTracker = SelectionTracker.Builder("saved sessions selection",
            binding.sessionsListFragmentRv,
            object: ItemKeyProvider<String>(SCOPE_CACHED) {
                override fun getKey(position: Int): String? {
                    return (binding.sessionsListFragmentRv.adapter as SessionsListAdapter).getKey(position)
                }

                override fun getPosition(key: String): Int {
                    return (binding.sessionsListFragmentRv.adapter as SessionsListAdapter).getPosition(key)
                }
            },
            object: ItemDetailsLookup<String>() {
                override fun getItemDetails(e: MotionEvent): ItemDetails<String>? {

                    val view = binding.sessionsListFragmentRv.findChildViewUnder(e.x, e.y)

                    return if (view != null) {
                        (binding.sessionsListFragmentRv.getChildViewHolder(view) as SessionsListAdapter.SessionsListItemViewHolder).getItemDetails()
                    } else null
                }
            },
            StorageStrategy.createStringStorage()
        ).withSelectionPredicate(SelectionPredicates.createSelectAnything()).build()

        viewModel.getSelection().let { selectedItems ->
            selectionTracker.setItemsSelected(selectedItems, true)
        }

        (binding.sessionsListFragmentRv.adapter as SessionsListAdapter).selectionTracker = selectionTracker

        selectionTracker.addObserver(object: SelectionTracker.SelectionObserver<String>() {
            override fun onItemStateChanged(key: String, selected: Boolean) {
                super.onItemStateChanged(key, selected)

                if (selected) {
                    viewModel.addDatabaseName(key)
                } else viewModel.removeDatabaseName(key)

                if (!selectionTracker.hasSelection()) {
                    binding.sessionsListFragmentTb.setTitle(R.string.setup_upper_container_choose_action_fragment_saved_sessions)
                    binding.sessionsListFragmentTb.setNavigationIcon(R.drawable.ic_arrow_back)
                    binding.sessionsListFragmentTb.menu.findItem(R.id.sessions_list_fragment_toolbar_menu_item_delete).isVisible =
                        false
                } else {
                    binding.sessionsListFragmentTb.title = selectionTracker.selection.size().toString() + " " + getString(
                        R.string.sessions_list_fragment_number_sessions_selected
                    )
                    binding.sessionsListFragmentTb.setNavigationIcon(R.drawable.baseline_clear_24)
                    binding.sessionsListFragmentTb.menu.findItem(R.id.sessions_list_fragment_toolbar_menu_item_delete).isVisible =
                        true
                }

            }
        })

        binding.sessionsListFragmentTb.setNavigationOnClickListener {
            if (selectionTracker.hasSelection()) {
                selectionTracker.clearSelection()
            } else activity?.onBackPressed()
        }

        binding.sessionsListFragmentTb.setOnMenuItemClickListener {
            when (it.itemId)  {
                R.id.sessions_list_fragment_toolbar_menu_item_delete -> {

                    val removedDatabasesNames = ArrayList<String>()

                    selectionTracker.selection.forEach { databaseName ->
                        StunWireApp.instance.deleteDatabase(databaseName)
                        removedDatabasesNames.add(databaseName)
                    }

                    selectionTracker.clearSelection()

                    for (index in 0 until removedDatabasesNames.size) {
                        val databaseIndex = (binding.sessionsListFragmentRv.adapter as SessionsListAdapter).indexOf(removedDatabasesNames.get(index))
                        (binding.sessionsListFragmentRv.adapter as SessionsListAdapter).remove(removedDatabasesNames.get(index))
                        binding.sessionsListFragmentRv.adapter!!.notifyItemRemoved(databaseIndex)
                    }

                    true
                }
                else -> {
                    false
                }
            }
        }

        binding.sessionsListFragmentRv.adapter!!.notifyDataSetChanged()

        return binding.root
    }
}