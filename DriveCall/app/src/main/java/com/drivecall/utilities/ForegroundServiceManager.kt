package com.drivecall.utilities

import android.content.Context
import android.content.Intent
import com.drivecall.services.DriveCallForegroundService

object ForegroundServiceManager {

    fun startService(context: Context) {
        Logger.service("Starting foreground service")
        val intent = Intent(context, DriveCallForegroundService::class.java)
        context.startForegroundService(intent)
    }

    fun stopService(context: Context) {
        Logger.service("Stopping foreground service")
        val intent = Intent(context, DriveCallForegroundService::class.java).apply {
            action = DriveCallForegroundService.ACTION_STOP_SERVICE
        }
        context.startService(intent)
    }

    fun updateStatus(context: Context, statusText: String) {
        val intent = Intent(context, DriveCallForegroundService::class.java).apply {
            action = DriveCallForegroundService.ACTION_UPDATE_STATUS
            putExtra(DriveCallForegroundService.EXTRA_STATUS_TEXT, statusText)
        }
        context.startService(intent)
    }
}
