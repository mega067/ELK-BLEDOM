package com.example.elkbledom

import android.Manifest
import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.elkbledom.ui.AudioMode
import com.example.elkbledom.ui.MainScreen
import com.example.elkbledom.ui.MainViewModel
import com.example.elkbledom.ui.TvScreen
import com.example.elkbledom.ui.theme.ELKBledomTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val isTV: Boolean by lazy {
        (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType ==
            Configuration.UI_MODE_TYPE_TELEVISION
    }

    private var permissionsGranted by mutableStateOf(false)
    private var bluetoothEnabled by mutableStateOf(false)

    // ── MediaProjection ───────────────────────────────────────────────────────

    private val mediaProjectionManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            getSystemService(MediaProjectionManager::class.java)
        else null
    }

    // Holds the consent-dialog result until the foreground service is confirmed running
    private var pendingResult: ActivityResult? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            // Service has called startForeground() — safe to call getMediaProjection() now
            val result = pendingResult ?: return
            pendingResult = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val mp = if (result.resultCode == RESULT_OK && result.data != null) {
                    mediaProjectionManager?.getMediaProjection(result.resultCode, result.data!!)
                } else null
                viewModel.onMediaProjection(mp, cancelled = mp == null)
            }
            // Unbind — service keeps running because we used startForegroundService()
            try { unbindService(this) } catch (_: IllegalArgumentException) { }
        }

        override fun onServiceDisconnected(name: ComponentName) {}
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            pendingResult = result
            // Start the foreground service first; getMediaProjection() fires in onServiceConnected
            val intent = Intent(this, MediaProjectionService::class.java)
            startForegroundService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            // User dismissed the system dialog
            viewModel.onMediaProjection(null, cancelled = true)
        }
    }

    fun launchProjectionConsent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaProjectionManager?.createScreenCaptureIntent()?.let {
                projectionLauncher.launch(it)
            }
        }
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (permissionsGranted) checkBluetooth()
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        bluetoothEnabled = bluetoothAdapter()?.isEnabled == true
    }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Stop the projection service (and release the MediaProjection) whenever the
        // user turns off music sync or switches back to mic mode.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var wasCapturing = false
                viewModel.ui
                    .map { (it.isMusicSync && it.audioMode == AudioMode.PLAYBACK) || it.isScreenSync }
                    .distinctUntilChanged()
                    .collect { isCapturing ->
                        if (!isCapturing && wasCapturing) {
                            stopService(Intent(this@MainActivity, MediaProjectionService::class.java))
                            viewModel.releaseProjection()
                        }
                        wasCapturing = isCapturing
                    }
            }
        }

        setContent {
            ELKBledomTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when {
                        !permissionsGranted -> PermissionGate(onRequest = ::requestPermissions)
                        !bluetoothEnabled   -> BluetoothGate(onEnable = ::requestBluetooth)
                        else -> if (isTV) {
                            TvScreen(
                                vm = viewModel,
                                onRequestMediaProjection = ::launchProjectionConsent,
                            )
                        } else {
                            MainScreen(
                                vm = viewModel,
                                onRequestMediaProjection = ::launchProjectionConsent,
                            )
                        }
                    }
                }
            }
        }

        requestPermissions()
    }

    private fun requestPermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            add(Manifest.permission.RECORD_AUDIO)
        }
        permLauncher.launch(perms.toTypedArray())
    }

    private fun checkBluetooth() {
        bluetoothEnabled = bluetoothAdapter()?.isEnabled == true
        if (!bluetoothEnabled) requestBluetooth()
    }

    private fun requestBluetooth() {
        enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
}

// ── Gating composables ────────────────────────────────────────────────────────

@androidx.compose.runtime.Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "ELK-BLEDOM needs Bluetooth and Microphone permissions to scan for " +
                "your LED strip and enable music sync.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Grant Permissions") }
    }
}

@androidx.compose.runtime.Composable
private fun BluetoothGate(onEnable: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Bluetooth is disabled. Please enable it to connect to your LED strip.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onEnable) { Text("Enable Bluetooth") }
    }
}
