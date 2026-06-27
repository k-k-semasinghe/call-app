package com.drivecall.utilities

import android.util.Log

object Logger {
    private const val TAG = "DriveCall"

    fun speech(message: String) {
        Log.d(TAG, "[Speech] $message")
    }

    fun matching(message: String) {
        Log.d(TAG, "[Matching] $message")
    }

    fun permission(message: String) {
        Log.d(TAG, "[Permission] $message")
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG, "[$tag] $message", throwable)
    }

    fun service(message: String) {
        Log.d(TAG, "[Service] $message")
    }

    fun debug(tag: String, message: String) {
        Log.d(TAG, "[$tag] $message")
    }

    fun info(tag: String, message: String) {
        Log.i(TAG, "[$tag] $message")
    }
}
