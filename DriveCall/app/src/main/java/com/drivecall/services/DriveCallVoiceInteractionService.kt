package com.drivecall.services

import android.content.Intent
import android.service.voice.VoiceInteractionService
import com.drivecall.MainActivity
import com.drivecall.utilities.Logger

class DriveCallVoiceInteractionService : VoiceInteractionService() {

    companion object {
        const val EXTRA_VOICE_ASSIST_TRIGGERED = "voice_assist_triggered"
    }

    override fun onReady() {
        super.onReady()
        Logger.service("VoiceInteractionService ready")
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        super.onLaunchVoiceAssistFromKeyguard()
        Logger.service("Voice assist launched from keyguard")
        launchApp()
    }

    override fun onShutdown() {
        super.onShutdown()
        Logger.service("VoiceInteractionService shut down")
    }

    private fun launchApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_VOICE_ASSIST_TRIGGERED, true)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Logger.error("VoiceInteractionService", "Failed to launch: ${e.message}")
        }
    }
}
