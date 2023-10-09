package com.xorec.stunwire.ui.saved_sessions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.Navigation
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.RecyclerView
import com.xorec.stunwire.R
import com.xorec.stunwire.StunWireApp
import com.xorec.stunwire.databinding.SessionsListItemBinding
import com.xorec.stunwire.getColorFromTheme

class SessionsListAdapter : RecyclerView.Adapter<SessionsListAdapter.SessionsListItemViewHolder>() {
    private val databaseList: ArrayList<String> = ArrayList()
    lateinit var selectionTracker: SelectionTracker<String>

    init {
        StunWireApp.instance.databaseList().forEach {
            if (!(it.endsWith("-shm") || it.endsWith("-wal") || it.endsWith("-journal"))) {
                databaseList.add(it)
            }
        }
    }

    inner class SessionsListItemViewHolder(private val binding: SessionsListItemBinding): RecyclerView.ViewHolder(binding.root) {
        private var adapterPosition: Int = -1
        private var itemDetails = SessionsListItemViewHolderDetails()

        inner class SessionsListItemViewHolderDetails: ItemDetailsLookup.ItemDetails<String>() {
            override fun getPosition(): Int {
                return adapterPosition
            }

            override fun getSelectionKey(): String? {
                return databaseList[adapterPosition]
            }

        }

        fun bind(position: Int) {
            adapterPosition = position

            binding.sessionsListItemTv.text = databaseList[position]

            if (selectionTracker.isSelected(databaseList[position])) {
                binding.sessionsListItemCv.setCardBackgroundColor(getColorFromTheme(binding.sessionsListItemCv.context, R.attr.sessionsListItemColorSelected))
            } else binding.sessionsListItemCv.setCardBackgroundColor(getColorFromTheme(binding.sessionsListItemCv.context, R.attr.sessionsListItemColor))

            itemView.setOnClickListener {
                StunWireApp.instance.sessionsRepo.openSessionDatabase(databaseList[position])
                Navigation.findNavController(itemView).navigate(R.id.action_sessionsListFragment_to_sessionFragment, null)
            }

        }

        fun getItemDetails(): SessionsListItemViewHolderDetails {
            return itemDetails
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionsListItemViewHolder {
        val binding = SessionsListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SessionsListItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionsListItemViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int {
        return databaseList.size
    }

    fun indexOf(databaseName: String): Int {
        return databaseList.indexOf(databaseName)
    }

    fun remove(databaseName: String) {
        databaseList.remove(databaseName)
    }

    fun getKey(position: Int): String {
        return databaseList[position]
    }

    fun getPosition(key: String): Int {
        return databaseList.indexOf(key)
    }
}