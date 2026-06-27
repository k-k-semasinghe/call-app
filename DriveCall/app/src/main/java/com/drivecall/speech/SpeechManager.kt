package com.drivecall.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.drivecall.utilities.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpeechManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var retryCount = 0
    private var isListening = false
    private var currentAttemptJob: Job? = null
    private var silenceTimerJob: Job? = null
    private var lastPartialText = ""

    private val _speechState = MutableStateFlow(SpeechState.IDLE)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    companion object {
        private const val MAX_RETRIES = 3
        private const val SILENCE_TIMEOUT_MS = 10000L
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Logger.speech("Ready for speech")
            _speechState.value = SpeechState.LISTENING
        }

        override fun onBeginningOfSpeech() {
            Logger.speech("Beginning of speech detected")
            resetSilenceTimer()
        }

        override fun onRmsChanged(rmsdB: Float) {
        }

        override fun onBufferReceived(buffer: ByteArray?) {
        }

        override fun onEndOfSpeech() {
            Logger.speech("End of speech detected")
            _speechState.value = SpeechState.PROCESSING
            cancelSilenceTimer()
        }

        override fun onError(error: Int) {
            val errorMsg = getErrorText(error)
            Logger.speech("Recognition error: $errorMsg (code: $error), retryCount=$retryCount")
            isListening = false
            cancelSilenceTimer()

            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        Logger.speech("Retrying ($retryCount/$MAX_RETRIES)")
                        _speechState.value = SpeechState.RETRYING
                        scope.launch {
                            delay(1000)
                            destroyRecognizer()
                            startListeningInternal()
                        }
                    } else {
                        _speechState.value = SpeechState.ERROR
                        _errorMessage.value = "Unable to recognize speech"
                        retryCount = 0
                    }
                }
                else -> {
                    _speechState.value = SpeechState.ERROR
                    _errorMessage.value = errorMsg
                }
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            cancelSilenceTimer()
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                Logger.speech("Recognized: '$text'")
                _recognizedText.value = text
                _speechState.value = SpeechState.RECOGNIZED
                retryCount = 0
                lastPartialText = ""
            } else if (lastPartialText.isNotBlank()) {
                Logger.speech("Using partial result as final: '$lastPartialText'")
                _recognizedText.value = lastPartialText
                _speechState.value = SpeechState.RECOGNIZED
                retryCount = 0
                lastPartialText = ""
            } else {
                Logger.speech("No recognition results (partial also empty)")
                handleEmptyResult()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                if (text.isNotBlank()) {
                    lastPartialText = text
                }
                Logger.speech("Partial: '$text'")
                _recognizedText.value = text
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
        }
    }

    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many requests"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language not supported"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Language unavailable"
            else -> "Unknown error ($errorCode)"
        }
    }

    fun startListening() {
        Logger.speech("startListening() called, current state=${_speechState.value}")
        retryCount = 0
        _errorMessage.value = null
        _recognizedText.value = ""
        lastPartialText = ""
        startListeningInternal()
    }

    private fun startListeningInternal() {
        if (isListening) {
            Logger.speech("Already listening, ignoring start")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Logger.speech("Speech recognition not available on device")
            _speechState.value = SpeechState.ERROR
            _errorMessage.value = "Speech recognition not available"
            return
        }

        Logger.speech("Starting speech recognition with locale=${Locale.getDefault()}")
        try {
            destroyRecognizer()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(recognitionListener)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            _speechState.value = SpeechState.LISTENING
            Logger.speech("Started listening")
            startSilenceTimer()
        } catch (e: Exception) {
            Logger.error("SpeechManager", "Failed to start speech recognition", e)
            if (retryCount < MAX_RETRIES) {
                retryCount++
                Logger.speech("Retrying start after audio focus delay ($retryCount/$MAX_RETRIES)")
                _speechState.value = SpeechState.RETRYING
                scope.launch {
                    delay(500)
                    startListeningInternal()
                }
            } else {
                _speechState.value = SpeechState.ERROR
                _errorMessage.value = "Failed to start speech recognition"
                isListening = false
                retryCount = 0
            }
        }
    }

    fun stopListening() {
        cancelSilenceTimer()
        destroyRecognizer()
        _speechState.value = SpeechState.IDLE
    }

    fun cancel() {
        cancelSilenceTimer()
        destroyRecognizer()
        _speechState.value = SpeechState.IDLE
        _recognizedText.value = ""
        _errorMessage.value = null
        retryCount = 0
    }

    private fun startSilenceTimer() {
        cancelSilenceTimer()
        silenceTimerJob = scope.launch {
            delay(SILENCE_TIMEOUT_MS)
            Logger.speech("Silence timeout ($SILENCE_TIMEOUT_MS ms) reached, stopping listening")
            if (isListening) {
                speechRecognizer?.stopListening()
            } else {
                Logger.speech("Silence timeout but not listening anymore")
            }
        }
    }

    private fun cancelSilenceTimer() {
        silenceTimerJob?.cancel()
        silenceTimerJob = null
    }

    private fun resetSilenceTimer() {
        if (isListening) {
            Logger.speech("Resetting silence timer (speech detected)")
            startSilenceTimer()
        }
    }

    private fun handleEmptyResult() {
        Logger.speech("Empty result with no partial fallback, not retrying")
        _speechState.value = SpeechState.ERROR
        _errorMessage.value = "Unable to recognize speech"
        retryCount = 0
    }

    private fun destroyRecognizer() {
        try {
            if (isListening) {
                speechRecognizer?.cancel()
                isListening = false
            }
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    fun destroy() {
        cancel()
        scope.cancel()
    }
}

enum class SpeechState {
    IDLE,
    LISTENING,
    PROCESSING,
    RECOGNIZED,
    RETRYING,
    ERROR
}
