package com.drivecall.models

enum class AppState {
    IDLE,
    LISTENING,
    RECOGNIZING,
    SEARCHING_CONTACT,
    CONFIRMING,
    MULTI_SELECT,
    CALLING,
    ERROR,
    SPEAKING
}
