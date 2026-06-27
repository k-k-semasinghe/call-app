package com.drivecall.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.drivecall.services.DriveCallForegroundService
import com.drivecall.utilities.Logger

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Logger.service("Boot completed, starting foreground service")
            val serviceIntent = Intent(context, DriveCallForegroundService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
