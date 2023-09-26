package com.example.stunwire.ui.session

import androidx.recyclerview.widget.DiffUtil
import com.example.stunwire.model.db.SessionMessage

class SessionMessageComparator: DiffUtil.ItemCallback<SessionMessage>() {
    override fun areItemsTheSame(oldItem: SessionMessage, newItem: SessionMessage): Boolean {
        return oldItem.isSent == newItem.isSent && oldItem.messageCode == newItem.messageCode
    }

    override fun areContentsTheSame(oldItem: SessionMessage, newItem: SessionMessage): Boolean {
        return oldItem.status == newItem.status
    }
}