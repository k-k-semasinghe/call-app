package com.drivecall.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.drivecall.utilities.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TextToSpeechManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingUtteranceCallback: (() -> Unit)? = null
    private var pendingSpeakRequest: Pair<String, (() -> Unit)?>? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        initialize()
    }

    private fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.ENGLISH)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Logger.error("TTS", "English language not supported for TTS")
                } else {
                    isInitialized = true
                    Logger.speech("TTS initialized successfully")
                    flushPendingSpeak()
                }
            } else {
                Logger.error("TTS", "Failed to initialize TTS")
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                val cb = pendingUtteranceCallback
                pendingUtteranceCallback = null
                cb?.invoke()
            }

            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                Logger.error("TTS", "TTS utterance error: $utteranceId")
            }
        })
    }

    private fun flushPendingSpeak() {
        val pending = pendingSpeakRequest
        if (pending != null) {
            pendingSpeakRequest = null
            speak(pending.first, pending.second)
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isInitialized) {
            Logger.speech("TTS not initialized, queuing: '$text'")
            pendingSpeakRequest = Pair(text, onDone)
            return
        }

        Logger.speech("Speaking: '$text'")
        pendingUtteranceCallback = onDone
        val utteranceId = "utt_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun speakAndListen(text: String, afterDone: () -> Unit) {
        speak(text, onDone = afterDone)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        pendingUtteranceCallback = null
    }

    fun isSpeakingNow(): Boolean = _isSpeaking.value

    fun destroy() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
