package com.drivecall

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.drivecall.databinding.ActivityMainBinding
import com.drivecall.models.AppState
import com.drivecall.permissions.PermissionManager
import com.drivecall.speech.SpeechState
import com.drivecall.utilities.ForegroundServiceManager
import com.drivecall.utilities.Logger
import com.drivecall.utilities.SpeedDialManager
import com.drivecall.viewmodels.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var speedDialManager: SpeedDialManager
    private var pendingSlot: Int? = null

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && pendingSlot != null) {
            val data = result.data
            if (data != null && data.data != null) {
                val contactUri = data.data!!
                val projection = arrayOf(
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER
                )
                val cursor: Cursor? = contentResolver.query(contactUri, projection, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val name = c.getString(c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                        val hasPhone = c.getString(c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER))?.toIntOrNull() ?: 0
                        if (hasPhone > 0) {
                            val phoneNumbers = getPhoneNumbers(contactUri)
                            if (phoneNumbers.isNotEmpty()) {
                                speedDialManager.setEntry(pendingSlot!!, name, phoneNumbers.first())
                                updateSpeedDialUI()
                            }
                        }
                    }
                }
                pendingSlot = null
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Logger.permission("All permissions granted")
            ForegroundServiceManager.startService(this)
            requestDefaultAssistantRole()
            startVoiceAssistFlow()
        } else {
            val denied = permissions.filter { !it.value }
            Logger.permission("Permissions denied: ${denied.keys.joinToString()}")
            updateUIForPermissionsDenied()
        }
    }

    private val roleManagerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Logger.info("MainActivity", "User accepted assistant role")
        } else {
            Logger.info("MainActivity", "User declined assistant role")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Logger.info("MainActivity", "Activity created")

        viewModel = MainViewModel(application)

        setupViews()
        observeViewModel()

        if (PermissionManager.hasAllPermissions(this)) {
            ForegroundServiceManager.startService(this)
            requestDefaultAssistantRole()
            startVoiceAssistFlow()
        } else {
            requestNeededPermissions()
        }
    }

    private fun setupViews() {
        speedDialManager = SpeedDialManager(this)
        binding.micButton.setOnClickListener {
            if (PermissionManager.hasAllPermissions(this)) {
                startVoiceAssistFlow()
            } else {
                requestNeededPermissions()
            }
        }
        setupSpeedDialSlots()
    }

    private fun setupSpeedDialSlots() {
        val slotIds = listOf(
            binding.slot1, binding.slot2, binding.slot3,
            binding.slot4, binding.slot5, binding.slot6,
            binding.slot7, binding.slot8, binding.slot9
        )
        for (btn in slotIds) {
            btn.setOnClickListener {
                if (!PermissionManager.hasAllPermissions(this)) {
                    requestNeededPermissions()
                    return@setOnClickListener
                }
                pendingSlot = btn.tag.toString().toIntOrNull()
                val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                contactPickerLauncher.launch(intent)
            }
            btn.setOnLongClickListener {
                val slot = btn.tag.toString().toIntOrNull() ?: return@setOnLongClickListener false
                if (speedDialManager.isSlotOccupied(slot)) {
                    AlertDialog.Builder(this)
                        .setTitle("Clear Speed Dial $slot")
                        .setMessage("Remove ${speedDialManager.getEntry(slot)?.name} from slot $slot?")
                        .setPositiveButton("Clear") { _, _ ->
                            speedDialManager.removeEntry(slot)
                            updateSpeedDialUI()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                true
            }
        }
        updateSpeedDialUI()
    }

    private fun updateSpeedDialUI() {
        val entries = speedDialManager.getAll()
        val slotMap = entries.associateBy { it.slot }
        val slotViews = listOf(
            binding.slot1 to 1, binding.slot2 to 2, binding.slot3 to 3,
            binding.slot4 to 4, binding.slot5 to 5, binding.slot6 to 6,
            binding.slot7 to 7, binding.slot8 to 8, binding.slot9 to 9
        )
        for ((view, slot) in slotViews) {
            val entry = slotMap[slot]
            view.text = if (entry != null) "$slot: ${entry.name}" else "$slot: --"
        }
    }

    private fun getPhoneNumbers(contactUri: Uri): List<String> {
        val numbers = mutableListOf<String>()
        val contactId = contactUri.lastPathSegment
        val phoneCursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )
        phoneCursor?.use { c ->
            while (c.moveToNext()) {
                val num = c.getString(0)
                if (!num.isNullOrBlank()) {
                    numbers.add(num.replace(Regex("[\\s\\-()]"), ""))
                }
            }
        }
        return numbers
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.appState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { state ->
                    updateUIForState(state)
                }
        }

        lifecycleScope.launch {
            viewModel.statusText.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { text ->
                    binding.statusText.text = text
                }
        }

        lifecycleScope.launch {
            viewModel.errorText.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { error ->
                    binding.errorText.visibility = if (error != null) View.VISIBLE else View.GONE
                    binding.errorText.text = error
                }
        }

        lifecycleScope.launch {
            viewModel.finishApp.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest {
                    binding.root.postDelayed({
                        if (!isFinishing && !isDestroyed) {
                            finishAndRemoveTask()
                        }
                    }, 600)
                }
        }

        lifecycleScope.launch {
            viewModel.speechManager.speechState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { state ->
                    updateMicVisualization(state)
                }
        }

        lifecycleScope.launch {
            viewModel.confirmationContact.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { contact ->
                    binding.confirmationGroup.visibility =
                        if (contact != null) View.VISIBLE else View.GONE
                    if (contact != null) {
                        binding.confirmationText.text = "Did you mean ${contact.name}?"
                    }
                }
        }

        lifecycleScope.launch {
            viewModel.candidates.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { list ->
                    if (list.isNotEmpty()) {
                        val options = list.mapIndexed { i, c -> "${i + 1}. ${c.name}" }.joinToString("  ")
                        binding.statusText.text = options
                    }
                }
        }
    }

    private fun updateUIForState(state: AppState) {
        when (state) {
            AppState.IDLE -> {
                binding.micButton.isEnabled = true
                binding.micButton.alpha = 1.0f
                binding.statusText.visibility = View.VISIBLE
                binding.progressIndicator.visibility = View.GONE
            }
            AppState.LISTENING -> {
                binding.micButton.isEnabled = true
                binding.micButton.alpha = 1.0f
                binding.statusText.visibility = View.VISIBLE
                binding.progressIndicator.visibility = View.VISIBLE
                binding.progressIndicator.setIndicatorColor(
                    ContextCompat.getColor(this, R.color.listening_color)
                )
            }
            AppState.RECOGNIZING -> {
                binding.micButton.isEnabled = false
                binding.micButton.alpha = 0.7f
                binding.statusText.visibility = View.VISIBLE
                binding.progressIndicator.visibility = View.VISIBLE
            }
            AppState.SEARCHING_CONTACT -> {
                binding.micButton.isEnabled = false
                binding.micButton.alpha = 0.7f
                binding.statusText.visibility = View.VISIBLE
                binding.progressIndicator.visibility = View.VISIBLE
                binding.progressIndicator.setIndicatorColor(
                    ContextCompat.getColor(this, R.color.searching_color)
                )
            }
            AppState.MULTI_SELECT -> {
                binding.micButton.isEnabled = false
                binding.micButton.alpha = 0.7f
                binding.statusText.visibility = View.VISIBLE
                binding.progressIndicator.visibility = View.GONE
                binding.confirmationGroup.visibility = View.GONE
            }
            AppState.CONFIRMING -> {
                binding.micButton.isEnabled = false
                binding.micButton.alpha = 0.7f
                binding.statusText.visibility = View.VISIBLE
                binding.confirmationGroup.visibility = View.VISIBLE
            }
            AppState.CALLING -> {
                binding.micButton.isEnabled = false
                binding.micButton.alpha = 0.7f
                binding.statusText.visibility = View.VISIBLE
                binding.progressIndicator.visibility = View.GONE
                binding.confirmationGroup.visibility = View.GONE
            }
            AppState.ERROR -> {
                binding.micButton.isEnabled = true
                binding.micButton.alpha = 1.0f
                binding.progressIndicator.visibility = View.GONE
            }
            AppState.SPEAKING -> {
                binding.micButton.isEnabled = false
                binding.micButton.alpha = 0.7f
                binding.progressIndicator.visibility = View.VISIBLE
            }
        }
    }

    private fun updateMicVisualization(speechState: SpeechState) {
        when (speechState) {
            SpeechState.LISTENING -> {
                binding.micButton.setImageResource(R.drawable.ic_mic_active)
            }
            SpeechState.RECOGNIZED -> {
                binding.micButton.setImageResource(R.drawable.ic_mic_done)
            }
            SpeechState.ERROR -> {
                binding.micButton.setImageResource(R.drawable.ic_mic_error)
            }
            else -> {
                binding.micButton.setImageResource(R.drawable.ic_mic_normal)
            }
        }
    }

    private fun requestDefaultAssistantRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
            ) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                roleManagerLauncher.launch(intent)
            }
        }
    }

    private fun requestNeededPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun updateUIForPermissionsDenied() {
        binding.statusText.text = "Permissions required"
        binding.errorText.visibility = View.VISIBLE
        binding.errorText.text = "Please grant all permissions in Settings"
    }

    private fun startVoiceAssistFlow() {
        viewModel.resetState()
        viewModel.prepareForVoiceAssist()
        viewModel.setAppState(AppState.SPEAKING, "Listening for contact name")
        viewModel.ttsManager.speak("Listening for contact name") {
            binding.root.post {
                viewModel.startListeningAfterTts()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (PermissionManager.hasAllPermissions(this)) {
            startVoiceAssistFlow()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::speedDialManager.isInitialized) {
            updateSpeedDialUI()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
