package com.example.elkbledom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class MediaProjectionService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent): IBinder = Binder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ELK-BLEDOM")
            .setContentText("Capturing phone audio for music sync")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setSilent(true)
            .setOngoing(true)
            .build()

        val serviceType = intent?.getIntExtra(EXTRA_SERVICE_TYPE, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            ?: ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            // Android 14+ throws SecurityException if token/permission isn't valid/granted yet
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ELKBledom::MediaProjectionWakeLock"
            ).apply {
                acquire()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun ensureChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Music Sync Capture", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Active while music sync is capturing phone audio" }
        )
    }

    companion object {
        const val CHANNEL_ID = "music_sync_capture"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_SERVICE_TYPE = "extra_service_type"
    }
}
