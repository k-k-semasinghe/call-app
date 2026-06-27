package com.drivecall.ui

import com.drivecall.models.AppState

data class MainUIState(
    val appState: AppState = AppState.IDLE,
    val statusText: String = "Tap the microphone to start",
    val errorText: String? = null,
    val confirmationContactName: String? = null
)
