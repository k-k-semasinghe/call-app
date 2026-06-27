package com.drivecall.viewmodels

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivecall.contacts.ContactRepository
import com.drivecall.fuzzy.ConfidenceLevel
import com.drivecall.fuzzy.FuzzySearchHelper
import com.drivecall.models.*
import com.drivecall.permissions.PermissionManager
import com.drivecall.speech.SpeechManager
import com.drivecall.speech.SpeechState
import com.drivecall.tts.TextToSpeechManager
import com.drivecall.utilities.CommandParser
import com.drivecall.utilities.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val speechManager = SpeechManager(application)
    val ttsManager = TextToSpeechManager(application)
    val contactRepository = ContactRepository(application)

    private val _appState = MutableStateFlow(AppState.IDLE)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _statusText = MutableStateFlow("Tap the microphone to start")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _errorText = MutableStateFlow<String?>(null)
    val errorText: StateFlow<String?> = _errorText.asStateFlow()

    private val _confirmationContact = MutableStateFlow<Contact?>(null)
    val confirmationContact: StateFlow<Contact?> = _confirmationContact.asStateFlow()

    private val _candidates = MutableStateFlow<List<Contact>>(emptyList())
    val candidates: StateFlow<List<Contact>> = _candidates.asStateFlow()

    private var isProcessing = false

    init {
        observeSpeechState()
    }

    private fun observeSpeechState() {
        viewModelScope.launch {
            speechManager.speechState.collect { state ->
                when (state) {
                    SpeechState.IDLE -> {
                        if (_appState.value != AppState.CONFIRMING &&
                            _appState.value != AppState.CALLING &&
                            _appState.value != AppState.MULTI_SELECT) {
                            _appState.value = AppState.IDLE
                        }
                    }
                    SpeechState.LISTENING -> {
                        if (_appState.value != AppState.CONFIRMING &&
                            _appState.value != AppState.MULTI_SELECT) {
                            _appState.value = AppState.LISTENING
                            _statusText.value = "Listening..."
                        }
                    }
                    SpeechState.PROCESSING -> {
                        if (_appState.value != AppState.MULTI_SELECT &&
                            _appState.value != AppState.CONFIRMING) {
                            _appState.value = AppState.RECOGNIZING
                            _statusText.value = "Recognizing..."
                        }
                    }
                    SpeechState.RECOGNIZED -> {
                        if (!isProcessing) {
                            processRecognizedSpeech()
                        }
                    }
                    SpeechState.RETRYING -> {
                        if (_appState.value != AppState.MULTI_SELECT &&
                            _appState.value != AppState.CONFIRMING) {
                            _statusText.value = "Retrying..."
                        }
                    }
                    SpeechState.ERROR -> {
                        if (_appState.value == AppState.MULTI_SELECT) {
                            isProcessing = false
                            _statusText.value = "Please say the number or name again"
                            speechManager.startListening()
                            return@collect
                        }
                        val error = speechManager.errorMessage.value ?: "Recognition error"
                        _errorText.value = error
                        _statusText.value = error
                        _appState.value = AppState.ERROR
                        if (error.contains("Unable to recognize", ignoreCase = true)) {
                            ttsManager.speak("Unable to recognize speech")
                        }
                    }
                }
            }
        }
    }

    fun startListening() {
        if (!PermissionManager.hasAllPermissions(getApplication())) {
            _statusText.value = "Permissions required"
            _errorText.value = "Please grant all permissions"
            return
        }

        if (ttsManager.isSpeakingNow()) {
            ttsManager.stop()
            delayAndStartListening()
            return
        }

        if (_appState.value == AppState.CONFIRMING) {
            return
        }

        isProcessing = false
        _errorText.value = null
        contactRepository.loadContacts()
        speechManager.startListening()
    }

    private fun delayAndStartListening() {
        viewModelScope.launch {
            delay(500)
            startListening()
        }
    }

    private fun processRecognizedSpeech() {
        if (isProcessing) return
        isProcessing = true

        val text = speechManager.recognizedText.value
        if (text.isBlank()) {
            isProcessing = false
            return
        }

        Logger.speech("Processing recognized text: '$text', state=${_appState.value}")

        if (_appState.value == AppState.MULTI_SELECT) {
            handleMultiSelectSpeech(text)
            return
        }

        if (_appState.value == AppState.CONFIRMING) {
            handleConfirmationSpeech(text)
            return
        }

        val command = CommandParser.parse(text)

        when (command.intent) {
            CommandIntent.CALL -> {
                handleCallCommand(command)
            }
            CommandIntent.UNKNOWN -> {
                if (contactRepository.getContacts().isNotEmpty()) {
                    handleCallCommand(
                        CommandResult(
                            intent = CommandIntent.CALL,
                            targetName = text,
                            rawText = text
                        )
                    )
                } else {
                    _statusText.value = "Command not recognized"
                    _appState.value = AppState.IDLE
                    ttsManager.speak("Command not recognized") {
                        isProcessing = false
                    }
                }
            }
            else -> {
                _statusText.value = "Command not recognized"
                _appState.value = AppState.IDLE
                ttsManager.speak("Command not recognized") {
                    isProcessing = false
                }
            }
        }
    }

    private fun handleCallCommand(command: CommandResult) {
        val targetName = command.targetName ?: run {
            _statusText.value = "No contact specified"
            _appState.value = AppState.IDLE
            ttsManager.speak("Please say the contact name") {
                speechManager.startListening()
            }
            return
        }

        _appState.value = AppState.SEARCHING_CONTACT
        _statusText.value = "Searching Contact..."
        Logger.matching("Searching for contact: '$targetName'")

        val contacts = contactRepository.getContacts()
        if (contacts.isEmpty()) {
            _statusText.value = "No contacts available"
            _appState.value = AppState.ERROR
            ttsManager.speak("No contacts found on your device") {
                isProcessing = false
            }
            return
        }

        val matches = FuzzySearchHelper.findBestMatch(targetName, contacts)

        if (matches.isEmpty()) {
            _statusText.value = "Contact not found"
            _appState.value = AppState.IDLE
            ttsManager.speak("I couldn't find that contact") {
                isProcessing = false
            }
            return
        }

        val bestMatch = matches[0]
        val confidence = FuzzySearchHelper.classifyConfidence(bestMatch.confidence)

        Logger.matching("Best match: ${bestMatch.contact.name} (${(bestMatch.confidence * 100).toInt()}%), level: $confidence")

        val closeMatches = matches.filter { it.confidence >= bestMatch.confidence - 0.25 }

        if (closeMatches.size > 1) {
            val candidateContacts = closeMatches.map { it.contact }
            _candidates.value = candidateContacts
            _appState.value = AppState.MULTI_SELECT
            val optionsText = candidateContacts.mapIndexed { i, c -> "${i + 1}. ${c.name}" }.joinToString(", ")
            _statusText.value = optionsText
            val spokenOptions = candidateContacts.mapIndexed { i, c -> "${i + 1}. ${c.name}" }.joinToString(". ")
            isProcessing = false
            ttsManager.speak("Multiple contacts found. $spokenOptions. Please say the number.") {
                speechManager.startListening()
            }
            return
        }

        when (confidence) {
            ConfidenceLevel.HIGH -> {
                placeCall(bestMatch.contact)
            }
            ConfidenceLevel.MEDIUM -> {
                _confirmationContact.value = bestMatch.contact
                _appState.value = AppState.CONFIRMING
                val contactName = bestMatch.contact.name
                _statusText.value = "Did you mean $contactName?"
                ttsManager.speakAndListen("Did you mean $contactName?") {
                    speechManager.startListening()
                }
            }
            ConfidenceLevel.LOW -> {
                _statusText.value = "Contact not found"
                _appState.value = AppState.IDLE
                ttsManager.speak("I couldn't find that contact") {
                    isProcessing = false
                }
            }
        }
    }

    fun confirmCall(confirmed: Boolean) {
        val contact = _confirmationContact.value ?: return
        _confirmationContact.value = null

        if (confirmed) {
            placeCall(contact)
        } else {
            _appState.value = AppState.IDLE
            _statusText.value = "Cancelled"
            isProcessing = false
            ttsManager.speak("Please say the contact name again") {
                speechManager.startListening()
            }
        }
    }

    private fun placeCall(contact: Contact) {
        _appState.value = AppState.CALLING
        _statusText.value = "Calling ${contact.name}..."
        Logger.service("Placing call to ${contact.name} at ${contact.phoneNumber}")

        ttsManager.speak("Calling ${contact.name}") {
            val context = getApplication<Application>()
            try {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:${contact.phoneNumber}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Logger.error("MainViewModel", "Failed to place call", e)
                _statusText.value = "Failed to place call"
                _appState.value = AppState.ERROR
            }
            isProcessing = false
        }
    }

    fun handleMultiSelectSpeech(text: String) {
        val candidates = _candidates.value
        if (candidates.isEmpty()) {
            _appState.value = AppState.IDLE
            isProcessing = false
            return
        }

        val normalized = text.trim().lowercase()

        val numberWords = mapOf(
            "one" to 0, "first" to 0, "1" to 0,
            "two" to 1, "second" to 1, "2" to 1,
            "three" to 2, "third" to 2, "3" to 2,
            "four" to 3, "fourth" to 3, "4" to 3,
            "five" to 4, "fifth" to 4, "5" to 4
        )

        val digitMatch = Regex("\\d+").find(normalized)
        if (digitMatch != null) {
            val num = digitMatch.value.toIntOrNull()
            if (num != null && num in 1..candidates.size) {
                _candidates.value = emptyList()
                placeCall(candidates[num - 1])
                return
            }
        }

        for ((word, index) in numberWords) {
            if (normalized == word || normalized.startsWith("$word ") || normalized.endsWith(" $word") || normalized.contains(" $word ") || normalized.contains(word)) {
                if (index < candidates.size) {
                    _candidates.value = emptyList()
                    placeCall(candidates[index])
                    return
                }
            }
        }

        val nameMatch = candidates.find { it.name.lowercase() in normalized || normalized in it.name.lowercase() }
        if (nameMatch != null) {
            _candidates.value = emptyList()
            placeCall(nameMatch)
            return
        }

        isProcessing = false
        ttsManager.speak("Please say the number or name of the contact") {
            speechManager.startListening()
        }
    }

    fun handleConfirmationSpeech(text: String) {
        val normalized = text.trim().lowercase()
        val affirmative = listOf("yes", "yeah", "yep", "correct", "right", "confirm", "okay", "ok", "sure", "please")
        val negative = listOf("no", "nope", "nah", "cancel", "stop", "never mind", "don't", "not")

        if (affirmative.any { normalized.startsWith(it) || normalized.contains(it) }) {
            confirmCall(true)
        } else if (negative.any { normalized.startsWith(it) || normalized.contains(it) }) {
            confirmCall(false)
        } else {
            isProcessing = false
            ttsManager.speak("Please say yes or no") {
                speechManager.startListening()
            }
        }
    }

    fun resetState() {
        speechManager.cancel()
        _appState.value = AppState.IDLE
        _statusText.value = "Tap the microphone to start"
        _errorText.value = null
        _confirmationContact.value = null
        _candidates.value = emptyList()
        isProcessing = false
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.destroy()
        ttsManager.destroy()
        contactRepository.cleanup()
    }
}
