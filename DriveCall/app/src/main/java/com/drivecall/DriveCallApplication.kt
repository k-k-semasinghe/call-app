package com.drivecall

import android.app.Application
import com.drivecall.notification.NotificationHelper
import com.drivecall.utilities.Logger

class DriveCallApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Logger.info("DriveCallApplication", "Application starting")
        NotificationHelper.createNotificationChannel(this)
    }
}
