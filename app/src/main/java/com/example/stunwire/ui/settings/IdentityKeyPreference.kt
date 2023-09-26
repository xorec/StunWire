package com.example.stunwire.ui.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.util.AttributeSet
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.core.content.ContextCompat.*
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.example.stunwire.PUBLIC_KEY_PATH
import com.example.stunwire.R
import com.example.stunwire.model.crypto.refreshMyIdentityKeyPair
import com.example.stunwire.getUriByFilename
import com.example.stunwire.openBitmap
import kotlinx.coroutines.*

class IdentityKeyPreference(private val context: Context, attributeSet: AttributeSet): Preference(context, attributeSet) {
    private val scope = MainScope()
    private lateinit var publicKey: ImageView
    private lateinit var refreshButton: Button
    private lateinit var shareButton: Button

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        publicKey = holder.findViewById(R.id.identity_key_preference_iv) as ImageView
        refreshButton = holder.findViewById(R.id.identity_key_refresh_preference_btn) as Button
        shareButton = holder.findViewById(R.id.identity_key_share_preference_btn) as Button

        publicKey.setImageBitmap(openBitmap(context.filesDir.absolutePath + PUBLIC_KEY_PATH))

        refreshButton.setOnClickListener {
            scope.launch {
                ResourcesCompat.getDrawable(context.resources, R.drawable.image_placeholder, context.theme)?.also {
                    publicKey.setImageBitmap(it.toBitmap(publicKey.width, publicKey.height))
                }
                withContext(Dispatchers.IO) {
                    refreshMyIdentityKeyPair(context)
                }
                withContext(Dispatchers.Main) {
                    publicKey.setImageBitmap(openBitmap(context.filesDir.absolutePath + PUBLIC_KEY_PATH))
                }
            }
        }

        shareButton.setOnClickListener {
            val uri = getUriByFilename(context, PUBLIC_KEY_PATH)
            if (uri == null) {
                Toast.makeText(context, getString(context,
                    R.string.preference_fragment_unable_to_share
                ), LENGTH_LONG).show()
                return@setOnClickListener
            }

            startActivity(context,
                createChooser(Intent(ACTION_SEND).also {
                it.type = "image/png"
                it.putExtra(EXTRA_STREAM, uri)
                it.clipData = ClipData("label", arrayOf("image/png"), ClipData.Item(uri))
                it.addFlags(FLAG_GRANT_READ_URI_PERMISSION)
            }, null), null)
        }
    }

    override fun onDetached() {
        scope.cancel()
        super.onDetached()
    }
}