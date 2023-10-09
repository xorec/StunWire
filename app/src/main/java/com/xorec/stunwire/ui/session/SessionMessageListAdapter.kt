package com.xorec.stunwire.ui.session

import android.app.Activity
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.navigation.findNavController
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xorec.stunwire.R
import com.xorec.stunwire.StunWireApp
import com.xorec.stunwire.model.db.SessionMessage
import com.xorec.stunwire.model.db.SessionMessageStatus
import com.xorec.stunwire.model.db.SessionMessageType
import java.nio.charset.Charset
import java.text.SimpleDateFormat

class SessionMessageListAdapter : PagingDataAdapter<SessionMessage, SessionMessageListAdapter.SessionMessageViewHolder>(
    SessionMessageComparator()
) {
    enum class ViewHolderType {
        VIEWHOLDER_TYPE_TEXT_SENT,
        VIEWHOLDER_TYPE_TEXT_RECEIVED,
        VIEWHOLDER_TYPE_IMAGE_SENT,
        VIEWHOLDER_TYPE_IMAGE_RECEIVED,
        VIEWHOLDER_TYPE_PLACEHOLDER
    }

    abstract inner class SessionMessageViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        private var isSent: Boolean = false
        private var messageCode: Int = -1
        protected lateinit var timestampTv: TextView

        protected fun setMessageInfo(message: SessionMessage) {
            isSent = message.isSent
            messageCode = message.messageCode
            timestampTv.text = SimpleDateFormat("HH:mm").format(message.timestamp)
        }

        fun getMessageInfo(): Pair<Boolean, Int> {
            return Pair(isSent, messageCode)
        }
    }

    abstract inner class SentSessionMessageViewHolder(itemView: View): SessionMessageViewHolder(itemView) {
        protected lateinit var timestampIv: ImageView
        open fun bind(message: SessionMessage) {
            super.setMessageInfo(message)

            when(message.status) {
                SessionMessageStatus.SESSION_MESSAGE_STATUS_DELIVERING -> {
                    timestampIv.setImageResource(R.drawable.baseline_hourglass_top_24)
                }
                SessionMessageStatus.SESSION_MESSAGE_STATUS_DELIVERED -> {
                    timestampIv.setImageResource(R.drawable.baseline_check_24)
                }
                SessionMessageStatus.SESSION_MESSAGE_STATUS_READ -> {
                    timestampIv.setImageResource(R.drawable.baseline_done_all_24)
                }
            }
        }
    }

    abstract inner class ReceivedSessionMessageViewHolder(itemView: View): SessionMessageViewHolder(itemView) {
        open fun bind(message: SessionMessage) {
            super.setMessageInfo(message)

            if (!message.isReadSent) {
                message.isReadSent = true
                StunWireApp.instance.sessionService?.sendReadMessage(message.messageCode)
            }
        }
    }

    inner class SentTextViewHolder(itemView: View): SentSessionMessageViewHolder(itemView) {
        init {
            timestampTv = itemView.findViewById(R.id.sent_session_text_item_timestamp_tv)
            timestampIv = itemView.findViewById(R.id.sent_session_text_status_iv)
        }

        private val textTv: TextView = itemView.findViewById(R.id.sent_session_text_item_tv)

        override fun bind(message: SessionMessage) {
            super.bind(message)
            textTv.text = String(message.data, Charset.forName("UTF-16"))
        }
    }

    inner class SentImageViewHolder(itemView: View): SentSessionMessageViewHolder(itemView) {
        init {
            timestampTv = itemView.findViewById(R.id.sent_session_image_item_timestamp_tv)
            timestampIv = itemView.findViewById(R.id.sent_session_image_status_iv)
        }

        private val imageIv: ImageView = itemView.findViewById(R.id.sent_session_image_item_iv)

        override fun bind(message: SessionMessage) {
            super.bind(message)
            imageIv.setImageBitmap(BitmapFactory.decodeByteArray(message.data, 0, message.data.size))
            imageIv.setOnClickListener {
                (it.context as Activity).findNavController(R.id.nav_host_fragment).navigate(
                    R.id.action_sessionFragment_to_imageFragment,
                    bundleOf(Pair("isSent", message.isSent), Pair("messageCode", message.messageCode)))
            }
        }
    }

    inner class ReceivedTextViewHolder(itemView: View): ReceivedSessionMessageViewHolder(itemView) {
        init {
            timestampTv = itemView.findViewById(R.id.received_session_text_item_timestamp_tv)
        }

        private val textTv: TextView = itemView.findViewById(R.id.received_session_message_item_tv)

        override fun bind(message: SessionMessage) {
            super.bind(message)
            textTv.text = String(message.data, Charset.forName("UTF-16"))
        }
    }

    inner class ReceivedImageViewHolder(itemView: View): ReceivedSessionMessageViewHolder(itemView) {
        init {
            timestampTv = itemView.findViewById(R.id.received_session_image_item_timestamp_tv)
        }

        private val imageIv: ImageView = itemView.findViewById(R.id.received_session_image_item_iv)

        override fun bind(message: SessionMessage) {
            super.bind(message)
            imageIv.setImageBitmap(BitmapFactory.decodeByteArray(message.data, 0, message.data.size))
            imageIv.setOnClickListener {
                (it.context as Activity).findNavController(R.id.nav_host_fragment).navigate(
                    R.id.action_sessionFragment_to_imageFragment,
                    bundleOf(Pair("isSent", message.isSent), Pair("messageCode", message.messageCode)))
            }
        }
    }

    inner class PlaceholderViewHolder(itemView: View): SessionMessageViewHolder(itemView) {

    }

    override fun getItemViewType(position: Int): Int {
        val sessionMessage = getItem(position) ?: return ViewHolderType.VIEWHOLDER_TYPE_PLACEHOLDER.ordinal

        return if (sessionMessage.isSent && sessionMessage.type == SessionMessageType.SESSION_MESSAGE_TYPE_TEXT.ordinal.toByte()) {
            ViewHolderType.VIEWHOLDER_TYPE_TEXT_SENT.ordinal
        } else if (sessionMessage.isSent && sessionMessage.type == SessionMessageType.SESSION_MESSAGE_TYPE_IMAGE.ordinal.toByte()) {
            ViewHolderType.VIEWHOLDER_TYPE_IMAGE_SENT.ordinal
        } else if (!sessionMessage.isSent && sessionMessage.type == SessionMessageType.SESSION_MESSAGE_TYPE_TEXT.ordinal.toByte()) {
            ViewHolderType.VIEWHOLDER_TYPE_TEXT_RECEIVED.ordinal
        } else ViewHolderType.VIEWHOLDER_TYPE_IMAGE_RECEIVED.ordinal
    }

    override fun onBindViewHolder(holder: SessionMessageViewHolder, position: Int) {
        val sessionMessage = getItem(position) ?: return

        return if (sessionMessage.isSent && sessionMessage.type == SessionMessageType.SESSION_MESSAGE_TYPE_TEXT.ordinal.toByte()) {
            (holder as SentTextViewHolder).bind(sessionMessage)
        } else if (sessionMessage.isSent && sessionMessage.type == SessionMessageType.SESSION_MESSAGE_TYPE_IMAGE.ordinal.toByte()) {
            (holder as SentImageViewHolder).bind(sessionMessage)
        } else if (!sessionMessage.isSent && sessionMessage.type == SessionMessageType.SESSION_MESSAGE_TYPE_TEXT.ordinal.toByte()) {
            (holder as ReceivedTextViewHolder).bind(sessionMessage)
        } else if (!sessionMessage.isSent && sessionMessage.type == SessionMessageType.SESSION_MESSAGE_TYPE_IMAGE.ordinal.toByte()) {
            (holder as ReceivedImageViewHolder).bind(sessionMessage)
        } else return
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionMessageViewHolder {
        return when (viewType) {
            ViewHolderType.VIEWHOLDER_TYPE_TEXT_SENT.ordinal -> {
                SentTextViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.session_fragment_sent_text_item, parent,false))
            }
            ViewHolderType.VIEWHOLDER_TYPE_IMAGE_SENT.ordinal -> {
                SentImageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.session_fragment_sent_image_item, parent,false))
            }
            ViewHolderType.VIEWHOLDER_TYPE_TEXT_RECEIVED.ordinal -> {
                ReceivedTextViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.session_fragment_received_text_item, parent,false))
            }
            ViewHolderType.VIEWHOLDER_TYPE_IMAGE_RECEIVED.ordinal -> {
                ReceivedImageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.session_fragment_received_image_item, parent,false))
            }
            else -> {
                PlaceholderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.session_fragment_placeholder_item, parent,false))
            }
        }
    }
}