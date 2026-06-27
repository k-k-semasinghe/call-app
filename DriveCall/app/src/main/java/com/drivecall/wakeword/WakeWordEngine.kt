package com.drivecall.wakeword

interface WakeWordEngine {
    fun startListening(callback: () -> Unit)
    fun stopListening()
    fun isListening(): Boolean
    fun destroy()
}
