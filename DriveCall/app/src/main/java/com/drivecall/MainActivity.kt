package com.drivecall

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
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
import com.drivecall.viewmodels.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Logger.permission("All permissions granted")
            ForegroundServiceManager.startService(this)
            viewModel.startListening()
        } else {
            val denied = permissions.filter { !it.value }
            Logger.permission("Permissions denied: ${denied.keys.joinToString()}")
            updateUIForPermissionsDenied()
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
        checkPermissions()
    }

    private fun setupViews() {
        binding.micButton.setOnClickListener {
            if (PermissionManager.hasAllPermissions(this)) {
                viewModel.startListening()
            } else {
                checkPermissions()
            }
        }
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

    private fun checkPermissions() {
        if (PermissionManager.hasAllPermissions(this)) {
            Logger.permission("All permissions already granted")
            ForegroundServiceManager.startService(this)
        } else {
            requestNeededPermissions()
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

    override fun onDestroy() {
        super.onDestroy()
    }
}
