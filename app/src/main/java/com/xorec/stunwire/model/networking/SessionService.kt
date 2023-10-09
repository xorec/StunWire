package com.xorec.stunwire.model.networking

import android.app.*
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.xorec.stunwire.R
import com.xorec.stunwire.*
import com.xorec.stunwire.ui.MainActivity
import kotlinx.coroutines.*

const val Notification_ID = 1
const val NOTIFICATION_CHANNEL_ID = "STUNWIRE SERVICE CHANNEL"

class SessionService : LifecycleService() {
    private val binder = SessionServiceBinder()
    private lateinit var sessionManager: SessionManager
    private lateinit var imageHandler: ImageHandler

    inner class SessionServiceBinder : Binder() {
        fun getService(): SessionService = this@SessionService
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (::sessionManager.isInitialized) {
            return START_NOT_STICKY
        }

        val displayMetrics = DisplayMetrics()
        (applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(displayMetrics)

        imageHandler = ImageHandler(displayMetrics.widthPixels, displayMetrics.heightPixels,
            displayMetrics.density)

        sessionManager = SessionManager(
            SessionType.values().find { it.name == intent!!.action }!!,
            lifecycleScope,
            StunWireApp.instance.sessionsRepo,
            imageHandler
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                "STUNWIRE SERVICE CHANNEL",
                "StunWire Service notifications channel",
                NotificationManager.IMPORTANCE_HIGH
            ).also {
                it.description = "StunWire Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(notificationChannel)
        }

        val notification = craftNotification(getString(R.string.notification_title_stunwire_service_active),
            getString(R.string.notification_content_text_retrieving_info), true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Notification_ID, notification, FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else startForeground(Notification_ID, notification)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        sessionManager.stop()
        super.onDestroy()
    }

    fun getSessionStatus(): LiveData<SessionStatus> {
        return sessionManager.sessionStatus
    }

    fun retrieveNetworkInfo(): NetworkAddress? {
        val stunServerAddress = PreferenceManager.getDefaultSharedPreferences(applicationContext).getString(
            STUN_SERVER_PREFERENCE_KEY,
            null)

        if (stunServerAddress == null) {
            stopError()
            return null
        }

        val result = sessionManager.retrieveNetworkInfo(stunServerAddress)

        if (result == null) {
            stopError()
            return null
        }

        updateNotification(getString(R.string.notification_title_stunwire_service_active), getString(
            R.string.notification_content_text_your_info) + result.toString(), true)

        return result
    }

    fun performHandshake(partnerAddress: NetworkAddress) {
        sessionManager.performHandshake(partnerAddress)
    }

    fun sendReadMessage(messageCode: Int) {
        sessionManager.sendReadMessage(messageCode)
    }

    fun sendImageMessage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var imageInputStream = contentResolver.openInputStream(uri)
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeStream(imageInputStream, null, options)

                var inSampleSize = 1
                while (options.outHeight / inSampleSize > 1024 || options.outWidth / inSampleSize > 1024) {
                    inSampleSize *= 2
                }

                options.inJustDecodeBounds = false
                options.inSampleSize = inSampleSize
                imageInputStream = contentResolver.openInputStream(uri)

                val exif = ExifInterface(imageInputStream!!)
                val imageOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

                imageInputStream = contentResolver.openInputStream(uri)
                var bitmap = BitmapFactory.decodeStream(imageInputStream, null, options)!!

                when(imageOrientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> {
                        bitmap = rotateImage(bitmap, 90)
                    }
                    ExifInterface.ORIENTATION_ROTATE_180 -> {
                        bitmap = rotateImage(bitmap, 180)
                    }
                    ExifInterface.ORIENTATION_ROTATE_270 -> {
                        bitmap = rotateImage(bitmap, 270)
                    }
                }

                sessionManager.sendImageMessage(imageHandler.scaleBitmapToFitSessionScreen(bitmapToByteArray(bitmap)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun disconnectLaunched() {
        sessionManager.disconnectLaunched()
    }

    fun sendPeerTypingMessage() {
        sessionManager.sendPeerTypingMessage()
    }

    fun sendTextMessage(text: String) {
        sessionManager.sendTextMessage(text)
    }

    fun deleteMessage(isSent: Boolean, messageCode: Int) {
        sessionManager.deleteMessage(isSent, messageCode)
    }

    private fun stopError() {
        updateNotification(getString(R.string.notification_title_stunwire_service_not_active), getString(
            R.string.notification_subtitle_error), false)

        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun craftNotification(titleString: String, contentText: String, isOngoing: Boolean): Notification {
        val intent = Intent(applicationContext, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this,
            NOTIFICATION_CHANNEL_ID)
            .setContentTitle(titleString)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(R.color.brand_color))
            .setOnlyAlertOnce(true)
            .setOngoing(isOngoing)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(title: String, subtitle: String, isOngoing: Boolean) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
            Notification_ID,
            craftNotification(title, subtitle, isOngoing)
        )
    }
}