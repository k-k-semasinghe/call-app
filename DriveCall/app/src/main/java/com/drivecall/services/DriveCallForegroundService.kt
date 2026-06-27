package com.drivecall.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.drivecall.notification.NotificationHelper
import com.drivecall.utilities.Logger

class DriveCallForegroundService : Service() {

    companion object {
        const val ACTION_UPDATE_STATUS = "com.drivecall.UPDATE_STATUS"
        const val EXTRA_STATUS_TEXT = "status_text"
        const val ACTION_STOP_SERVICE = "com.drivecall.STOP_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        Logger.service("Foreground service created")
        NotificationHelper.createNotificationChannel(this)
        val notification = NotificationHelper.buildNotification(this, "DriveCall is active")
        startForeground(1001, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.service("Foreground service onStartCommand")

        when (intent?.action) {
            ACTION_UPDATE_STATUS -> {
                val statusText = intent.getStringExtra(EXTRA_STATUS_TEXT) ?: "DriveCall is active"
                updateNotification(statusText)
            }
            ACTION_STOP_SERVICE -> {
                Logger.service("Stopping foreground service")
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Logger.service("Foreground service destroyed")
        super.onDestroy()
    }

    private fun updateNotification(statusText: String) {
        val notification = NotificationHelper.buildNotification(this, statusText)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(1001, notification)
    }
}
